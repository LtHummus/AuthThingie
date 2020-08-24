name := """auththingie"""
organization := "com.lthummus"

version := "0.2.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.3"
scalacOptions := Seq("-target:jvm-1.8")

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

javaOptions in Universal ++= Seq(
  "-Dpidfile.path=/dev/null"
)

resolvers += "OneHippo" at "https://maven.onehippo.com/maven2/"

libraryDependencies ++= List(
  guice,
  ws,

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
  "com.adrianhurt" %% "play-bootstrap" % "1.6.1-P28-B4",

  //duo
  "com.duosecurity" % "DuoWeb" % "1.1-20150226"

)


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.lthummus.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.lthummus.binders._"
