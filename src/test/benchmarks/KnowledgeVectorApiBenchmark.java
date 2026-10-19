import org.apache.lucene.util.VectorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

 * Head-to-head benchmark for the Java Vector API (Project Panama) on
 * Lucene's KNNVector dot-product path.
 *
 * The number that matters for the Wissens­datenbank is the per-query
 * latency. Each chat query embeds the user question (one vector) and
 * Lucene compares it against every KNNField vector in the index. For
 * a small KB this is a brute-force scan; for a large KB Lucene
 * walks the HNSW graph and only re-scores a candidate set. Either
 * way, the inner loop is the same SIMD-friendly dot product that
 * this benchmark measures.
 *
 * Run once with the incubator flag (production server setting) and
 * once without (the previous default). The shell loop in
 * {@code vector-api-benchmark.sh} runs both and prints the diff.
 *
 * Usage:
 *   {@code java --add-modules=jdk.incubator.vector -cp <classpath> KnowledgeVectorApiBenchmark}
 *   (the {@code --add-modules} flag is what controls which provider
 *    Lucene picks; the same JVM always reports the same provider)
 */
public class KnowledgeVectorApiBenchmark {

     *  main memory, and out-of-cache. The script runs the benchmark
     *  at each size and reports the per-size speedup. */
    private static final int[] CORPUS_SIZES = { 1_000, 10_000, 50_000 };
    private static final int    VECTOR_DIM     = 384;
    private static final int    NUM_ITERATIONS = 250;
    private static final int    TOP_K          = 32;
    private static final long   RNG_SEED       = 42L;

    private final List<float[]> corpus;
    private final float[]       query;
    private final double[]      scores;

    public KnowledgeVectorApiBenchmark(List<float[]> corpus, float[] query) {
        this.corpus = corpus;
        this.query = query;
        this.scores = new double[corpus.size()];
    }

    private enum VectorizationMode { PANAMA, SCALAR }

     * Reads Lucene's lazy {@code VectorizationProvider} singleton to
     * figure out which code path the JVM is actually running. The
     * {@code Holder} class is package-private, so we need
     * {@code setAccessible(true)} on the field — the earlier version
     * of this benchmark forgot that and silently swallowed the
     * {@code IllegalAccessException}, which made it always report
     * SCALAR even when the Vector API was clearly enabled (the INFO
     * log "Java vector incubator API enabled" comes from the
     * PanamaVectorizationProvider constructor and is printed only when
     * the module is loaded, so the log is the ground truth for which
     * provider is live).
     */
    private static VectorizationMode detectActiveMode() {
        try {
            Class<?> holder = Class.forName("org.apache.lucene.internal.vectorization.VectorizationProvider$Holder");
            java.lang.reflect.Field f = holder.getDeclaredField("INSTANCE");
            f.setAccessible(true);
            Object instance = f.get(null);
            if (instance == null) return VectorizationMode.SCALAR;
            String className = instance.getClass().getName();
            if (className.contains("Panama")) return VectorizationMode.PANAMA;
            return VectorizationMode.SCALAR;
        } catch (Throwable t) {
            return VectorizationMode.SCALAR;
        }
    }

    private static float cosine(float[] a, float[] b) {
        return VectorUtil.cosine(a, b);
    }

     * One cosine scan against every document, then keep the top-K
     * (mirrors what Lucene does after the HNSW graph returns its
     * candidate list and before the final reranking). Equivalent to
     * the production KB search path on a small index (where Lucene
     * falls back to brute-force scan) and the per-candidate dot product
     * on a large HNSW index.
     */
    private void scanOnce() {
        float qn = 0f;
        for (float v : query) qn += v * v;
        qn = (float) Math.sqrt(qn);
        if (qn == 0f) qn = 1f;
        for (int i = 0; i < corpus.size(); i++) {
            float[] doc = corpus.get(i);
            float dn = 0f;
            for (float v : doc) dn += v * v;
            dn = (float) Math.sqrt(dn);
            if (dn == 0f) dn = 1f;
            scores[i] = cosine(query, doc) / (qn * dn);
        }
        java.util.Arrays.sort(scores);
    }

    public double run() {
        int warmup = 20;
        for (int i = 0; i < warmup; i++) scanOnce();
        System.gc();
        long t0 = System.nanoTime();
        for (int i = 0; i < NUM_ITERATIONS; i++) scanOnce();
        long t1 = System.nanoTime();
        return (t1 - t0) / 1_000_000.0;
    }

    private static List<float[]> buildCorpus(Random rng, int n, int dim) {
        List<float[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            float[] v = new float[dim];
            for (int j = 0; j < dim; j++) {
                v[j] = (float) rng.nextGaussian();
            }
            out.add(v);
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        int[] sizes = CORPUS_SIZES;
        if (args.length > 0 && args[0].equals("--sizes")) {
            String[] parts = args[1].split(",");
            sizes = new int[parts.length];
            for (int i = 0; i < parts.length; i++) sizes[i] = Integer.parseInt(parts[i].trim());
        }

        VectorizationMode mode = detectActiveMode();
        Random rng = new Random(RNG_SEED);

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  StackLens KB vector benchmark                               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  Java                 : " + System.getProperty("java.version"));
        System.out.println("  VM                   : " + System.getProperty("java.vm.name"));
        System.out.println("  Available cores      : " + Runtime.getRuntime().availableProcessors());
        System.out.println("  Lucene Vectorization : " + mode);
        System.out.println("  iterations per run   : " + NUM_ITERATIONS);
        System.out.println("  vector dimension     : " + VECTOR_DIM);
        System.out.println("  top-K                : " + TOP_K);
        System.out.println();
        System.out.println("  ┌─────────────────┬─────────────┬─────────────┬────────────┬────────────┐");
        System.out.println("  │ corpus size       │ total ms     │ ms / scan    │ vectors/s  │ µs/vector  │");
        System.out.println("  ├─────────────────┼─────────────┼─────────────┼────────────┼────────────┤");

        for (int n : sizes) {
            Random r = new Random(RNG_SEED);
            List<float[]> corpus = buildCorpus(r, n, VECTOR_DIM);
            float[] query = new float[VECTOR_DIM];
            for (int i = 0; i < VECTOR_DIM; i++) query[i] = (float) r.nextGaussian();

            KnowledgeVectorApiBenchmark bench = new KnowledgeVectorApiBenchmark(corpus, query);
            System.gc();
            double totalMs = bench.run();
            double avgPerScan = totalMs / (double) NUM_ITERATIONS;
            double vectorsPerSec = (double) NUM_ITERATIONS * corpus.size() / (totalMs / 1000.0);
            double usPerVector = 1_000_000.0 / vectorsPerSec;
            System.out.printf("  │ %-15s  │ %-11s  │ %-11s  │ %-10s  │ %-10s  │%n",
                String.format("%,d", n) + " vectors",
                String.format("%,.1f", totalMs),
                String.format("%,.4f", avgPerScan),
                String.format("%,.0f", vectorsPerSec),
                String.format("%,.2f", usPerVector));
        }
        System.out.println("  └─────────────────┴─────────────┴─────────────┴────────────┴────────────┘");
        System.out.println();
        System.out.println("  run this benchmark twice — once with and once without");
        System.out.println("  --add-modules=jdk.incubator.vector — to compare the two");
        System.out.println("  modes side by side. The numbers above are single-thread.");
        System.out.println();
        System.out.println("  Production note: the build.gradle.kts test/bootRun tasks");
        System.out.println("  and docker/stacklens/entrypoint.sh all set the flag so the");
        System.out.println("  Wissens­datenbank always uses the Vector API at runtime.");
    }
}
