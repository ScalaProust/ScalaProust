lazy val hereDir:File = file(".")
lazy val scalaSTM = RootProject(uri(s"git:file://${hereDir.getCanonicalPath}/deps/scala-stm/"))


lazy val root = (project in hereDir dependsOn(scalaSTM)).
	settings(
		name := "scala-proust",
		version := "1.0",
		scalaVersion := "2.11.8",
		mainClass in (Compile, run) := Some("scala.concurrent.stm.stamp.pqthroughputtest.PQThroughputTest")
		//EclipseKeys.withSource := true
	)

libraryDependencies ++= Seq(
  compilerPlugin("com.github.wheaties" %% "twotails" % "0.3.1" cross CrossVersion.full),
  "com.github.wheaties" %% "twotails-annotations" % "0.3.1" cross CrossVersion.full,
  "org.scala-lang" % "scala-reflect" % scalaVersion.value 
)
