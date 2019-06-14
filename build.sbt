name := """traefikcop"""
organization := "com.lthummus"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.2" % Test
libraryDependencies += "com.github.kxbmap" %% "configs" % "0.4.4"
libraryDependencies += "commons-codec" % "commons-codec" % "1.12"
libraryDependencies += "at.favre.lib" % "bcrypt" % "0.8.0"

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.lthummus.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.lthummus.binders._"
