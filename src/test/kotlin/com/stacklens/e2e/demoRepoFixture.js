import { mkdirSync, realpathSync, rmSync, writeFileSync } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const e2eDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(e2eDir, '..', '..', '..', '..', '..', '..');
mkdirSync(path.join(repoRoot, 'build', 'e2e-demo-repos'), { recursive: true });
const fixtureRoot = realpathSync.native(path.join(repoRoot, 'build', 'e2e-demo-repos'));

const SETTINGS_GRADLE_KTS = `rootProject.name = "demo-checkout-service"
`;

const BUILD_GRADLE_KTS = `plugins {
    kotlin("jvm") version "1.9.24"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:3.3.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
}

application {
    mainClass.set("com.shop.checkout.CheckoutApplicationKt")
}
`;

const CHECKOUT_APPLICATION_KT = `package com.shop.checkout

fun main() {
    println("demo-checkout-service started")
}
`;

const CHECKOUT_SERVICE_KT = `package com.shop.checkout

class Address(val street: String, val zipCode: String)

class Order(val id: Long, val userId: Long, val billingAddress: Address?)

class CheckoutService {
    fun completeOrder(order: Order): Order {
        val billingAddress = order.billingAddress
            ?: throw NullPointerException("billingAddress must not be null")
        println("order \${order.id} completed for \${billingAddress.street}")
        return order
    }
}
`;

const SUBSCRIPTION_RENEWAL_WORKER_KT = `package com.shop.checkout

class SubscriptionRenewalWorker(private val checkoutService: CheckoutService) {
    fun renew(order: Order): Order = checkoutService.completeOrder(order)
}
`;

const EVENT_CONSUMER_KT = `package com.shop.checkout

class EventConsumer(private val renewalWorker: SubscriptionRenewalWorker) {
    fun onMessage(order: Order): Order = renewalWorker.renew(order)
}
`;

const BILLING_DB_KT = `package com.shop.billing

class BillingDb(private val maximumPoolSize: Int = 50) {
    fun reportActiveConnections(active: Int): String = "HikariPool-1 active=\$active/\$maximumPoolSize"
}
`;

const FIXTURE_FILES = {
  'settings.gradle.kts': SETTINGS_GRADLE_KTS,
  'build.gradle.kts': BUILD_GRADLE_KTS,
  'src/main/kotlin/com/shop/checkout/CheckoutApplication.kt': CHECKOUT_APPLICATION_KT,
  'src/main/kotlin/com/shop/checkout/CheckoutService.kt': CHECKOUT_SERVICE_KT,
  'src/main/kotlin/com/shop/checkout/SubscriptionRenewalWorker.kt': SUBSCRIPTION_RENEWAL_WORKER_KT,
  'src/main/kotlin/com/shop/checkout/EventConsumer.kt': EVENT_CONSUMER_KT,
  'src/main/kotlin/com/shop/billing/BillingDb.kt': BILLING_DB_KT,
};

let sequence = 0;

function slugify(label) {
  return String(label).replace(/[^A-Za-z0-9._-]+/g, '-');
}

export function createDemoRepoFixture(label) {
  sequence += 1;
  const target = path.join(fixtureRoot, `${slugify(label)}-${process.pid}-${sequence}`);
  removeDemoRepoFixture(target);
  mkdirSync(target, { recursive: true });
  for (const [relativePath, content] of Object.entries(FIXTURE_FILES)) {
    const file = path.join(target, relativePath);
    mkdirSync(path.dirname(file), { recursive: true });
    writeFileSync(file, content, 'utf8');
  }
  return realpathSync.native(target);
}

export function removeDemoRepoFixture(fixturePath) {
  if (!fixturePath) return;
  if (!fixturePath.startsWith(`${fixtureRoot}${path.sep}`)) return;
  rmSync(fixturePath, { recursive: true, force: true });
}
