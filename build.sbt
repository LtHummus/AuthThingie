name := """auththingie"""
organization := "com.lthummus"

version := "0.1.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.3"


javaOptions in Universal ++= Seq(
  "-Dpidfile.path=/dev/null"
)

libraryDependencies ++= List(
  guice,

  //cats (meow)
  "org.typelevel" %% "cats-core" % "2.0.0",

  //testing
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,
  "org.mockito" %% "mockito-scala" % "1.5.11" % "test",

  "commons-codec" % "commons-codec" % "1.13",
  "commons-io" % "commons-io" % "2.6",
  "org.apache.commons" % "commons-lang3" % "3.9",

  //TOTP + QR
  "at.favre.lib" % "bcrypt" % "0.8.0",
  "com.google.zxing" % "core" % "3.4.0",
  "com.github.scopt" %% "scopt" % "3.7.1",

  //look nice
  "com.adrianhurt" %% "play-bootstrap" % "1.5-P27-B4"
)


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.lthummus.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.lthummus.binders._"
