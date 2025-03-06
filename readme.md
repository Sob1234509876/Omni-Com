Omni Platform
===
A part of Omni applications, used as an execution platform for the applications.

Compile
===
Simply run command <code>gradlew build</code>. If you cannot download Gradle using this wrapper, please change the
distribution URL to the one you prefer. This wrapper uses the Tencent Mirror.

Tutorial
===
Here is how the platform works through the <code>java</code> command :
<br>

1. Invoke <code>top.sob.platform.Main.main(String[])</code>
2. Resolve the passed in classpath *
3. Create a PlatformBuilder (<code>top.sob.platform.api.PlatformBuilder</code>) and build a new platform
4. Execute the created platform using the <code>exec()</code> method

\* The classpath is passed through the <code>java -D</code> option, the classpath`s key is "cp". One can pass in
multiple classpath by adding semicolons between. Note that the classpath will be automatically expanded, to close the
auto-expand process please pass in another option with the key "dae" and the value "true".