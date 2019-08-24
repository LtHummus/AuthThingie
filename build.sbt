name := """auththingie"""
organization := "com.lthummus"

version := "0.0.4"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

libraryDependencies ++= List(
  guice,

  //testing
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.2" % Test,
  "org.mockito" %% "mockito-scala" % "1.5.11" % "test",

  "com.github.kxbmap" %% "configs" % "0.4.4",
  "commons-codec" % "commons-codec" % "1.12",
  "commons-io" % "commons-io" % "2.6",

  //TOTP + QR
  "at.favre.lib" % "bcrypt" % "0.8.0",
  "com.google.zxing" % "core" % "3.4.0",
  "commons-codec" % "commons-codec" % "1.12",
  "com.github.scopt" %% "scopt" % "3.7.1",

  //look nice
  "com.adrianhurt" %% "play-bootstrap" % "1.5-P27-B4"
)


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.lthummus.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.lthummus.binders._"
