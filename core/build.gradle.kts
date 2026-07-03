import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * KMP 도메인 모듈 — ai/ 인제스트·린트 파이프라인과 그것이 참조하는 순수 도메인 타입.
 *
 * 타깃은 android + jvm 두 개만 둔다: android는 기존 :app 소비용, jvm은 데스크톱 앱
 * 트랙의 선행 조건이자 "commonMain에 Android 의존성이 스며들지 않았다"는 것을
 * 컴파일러 수준에서 강제하는 게이트다 (CLAUDE.md 규칙 4의 기계적 검증). iOS 타깃은
 * 실수요가 생길 때 추가한다 — 지금 추가하면 빌드 시간만 내고 아무도 소비하지 않는다.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        // 유닛 테스트는 commonTest가 아니라 jvmTest에 둔다: 기존 테스트가 JUnit4의
        // org.junit.Assert와 kotlinx.coroutines.runBlocking(JVM 전용)을 그대로 쓰는데,
        // commonTest로 가려면 kotlin.test + runTest로의 전환(= 테스트 코드 수정)이
        // 필요하다. 이번 배치는 순수 재배치가 원칙이므로 코드 무수정으로 옮길 수 있는
        // jvmTest를 택했다. android 타깃까지 같은 테스트를 돌릴 필요가 생기면 그때
        // kotlin.test 전환과 함께 commonTest로 승격한다.
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// AGP가 등록하는 `test` 앵커는 android 변형 유닛 테스트만 돌린다. 이 모듈의 실제
// 테스트는 jvmTest에 있으므로(위 sourceSets 주석 참고), 루트에서 `./gradlew test` 한
// 번으로 :app과 :core의 테스트가 전부 도는 기존 워크플로를 유지하려면 이 연결이 필요하다.
// `test`는 AGP가 변형 확정 후에야 등록하므로 named()로 지금 조회하면 실패한다 —
// matching{}은 태스크가 실체화되는 시점에 지연 적용되어 등록 순서에 안전하다.
tasks.matching { it.name == "test" }.configureEach {
    dependsOn(tasks.named("jvmTest"))
}

android {
    namespace = "com.example.storymind.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
