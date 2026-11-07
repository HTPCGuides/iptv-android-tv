# SnakeYAML-Engine (m3u_gen_acestream config import) resolves scalar tags by class name
# and builds its constructors from a static table; keep the engine intact rather than
# discovering which of its classes R8 strips.
-keep class org.snakeyaml.engine.** { *; }
-dontwarn org.snakeyaml.engine.**
