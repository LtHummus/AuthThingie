addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.19")
addSbtPlugin("org.foundweekends.giter8" % "sbt-giter8-scaffold" % "0.11.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

// needed for sbt 1.8 (see https://github.com/playframework/playframework/releases/tag/2.8.19 )
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
