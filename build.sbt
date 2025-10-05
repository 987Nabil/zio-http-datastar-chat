name := "zio-http-datastar-chat"

version := "0.1.0"

scalaVersion := "3.7.3"

ThisBuild / resolvers += Resolver.sonatypeCentralSnapshots

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "2.1.21",
  "dev.zio" %% "zio-http" % "3.5.1+18-c8a19832-SNAPSHOT",
  "dev.zio" %% "zio-http-datastar-sdk" % "3.5.1+18-c8a19832-SNAPSHOT",
  "dev.zio" %% "zio-schema" % "1.7.5",
  "dev.zio" %% "zio-schema-derivation" % "1.7.5"
  )

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation"
)

