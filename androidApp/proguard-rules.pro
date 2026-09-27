# -------------------------------------------------------------------------
# R8/ProGuard 混淆配置文件
# -------------------------------------------------------------------------

# 基础全局设置 ---
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,AnnotationDefault,*Annotation*

# 依赖注入 (Koin) ---
# v3.71.2（体积专项实测）：去掉原先的 `-keep class org.koin.** { *; }` 整包保留。
# 实测该规则会拦住 R8 对未引用 Koin 代码的裁剪（koin-androidx-fragment、android.scope、
# 未用到的 dsl 等），单条规则即占 APK 170,590 B（-3.45%）。
# 安全性依据：Koin 的定义/注入全部由 koin-annotations 的 KSP 生成代码直接引用，属于
# 可达代码；Koin 自身不经反射解析自身类，故不再需要整包 keep。
# 已保留下方「按注解保留」规则（Module 类、@Single/@Factory 构造函数等）。

# 保留 Koin Annotations 及其生成的模块 (KSP 路径)
-keep class org.koin.ksp.generated.** { *; }
-keep @org.koin.core.annotation.Module class * { *; }

# 确保 Koin 能够调用被注解类的构造函数进行依赖注入
-keepclassmembers class * {
    @org.koin.core.annotation.Single <init>(...);
    @org.koin.core.annotation.Factory <init>(...);
    @org.koin.core.annotation.KoinViewModel <init>(...);
    @org.koin.core.annotation.Named <init>(...);
}

# 原生组件与 WorkManager
-keep public class * extends android.appwidget.AppWidgetProvider {
    public void *(android.content.Context, android.content.Intent);
    <init>();
}
-keep class com.shangkeschedule.widget.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# 网络库 (Ktor)
-dontwarn io.ktor.**

# 日志与极致优化
-keep class org.slf4j.impl.** { *; }

# 移除 Android 系统调试日志 (v/d/i/w)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# 数据解析 (Kotlinx Serialization & Wire Protobuf) ---
-keep class kotlin.Metadata { *; }
-keep @kotlinx.serialization.Serializable class * { ** Companion; }
-keepclassmembers class * { *** write$Self(...); <init>(int, ...); }
-keep class **$$serializer { *; }

-keep class * implements com.squareup.wire.Message {
    <fields>;
    <methods>;
}
-keep class * implements com.squareup.wire.WireEnum { *; }
-keepclassmembers class * implements com.squareup.wire.Message {
    public static *** ADAPTER;
}
-keep class * extends com.squareup.wire.ProtoAdapter { *; }


# 数据模型与数据库
-dontwarn androidx.sqlite.**
# androidx.sqlite 通过 JNI 加载 bundled 原生库，驱动类需保留（库级 keep，体积可忽略）
-keep class androidx.sqlite.** { *; }
# Room3 Entity / Dao / Database：仅保留被注解的必要类，不再整包 keep
-keep @androidx.room3.Entity class * { *; }
-keep @androidx.room3.Dao interface * { *; }
-keep @androidx.room3.Database class * { *; }
# KSP 生成的实现类（Room 通过反射/构造器回调，需保留实现与成员）
-keep class * extends androidx.room3.RoomDatabase { *; }
-keep class * implements androidx.room3.Dao { *; }