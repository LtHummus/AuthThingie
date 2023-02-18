name := """auththingie"""
organization := "com.lthummus"

version := "0.2.2"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(BuildInfoPlugin).settings(
  buildInfoKeys := Seq[BuildInfoKey](name, version),
  buildInfoPackage := "auththingieversion"
)

buildInfoOptions += BuildInfoOption.BuildTime

scalaVersion := "2.13.10"

javaOptions in Universal ++= Seq(
  "-Dpidfile.path=/dev/null"
)

libraryDependencies ++= List(
  guice,
  ws,

  //cats (meow)
  "org.typelevel" %% "cats-core" % "2.9.0",

  //testing
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test,
  "org.mockito" %% "mockito-scala" % "1.17.12" % "test",

  "commons-codec" % "commons-codec" % "1.15",
  "commons-io" % "commons-io" % "2.11.0",
  "org.apache.commons" % "commons-lang3" % "3.12.0",

  //TOTP + QR
  "at.favre.lib" % "bcrypt" % "0.10.2",
  "com.google.zxing" % "core" % "3.5.1",
  "com.github.scopt" %% "scopt" % "4.1.0",

  //look nice
  "com.adrianhurt" %% "play-bootstrap" % "1.6.1-P28-B4"

)


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.lthummus.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.lthummus.binders._"
