name := "zio-http-datastar-chat"

version := "0.1.0"

scalaVersion := "3.7.3"

Global / onChangedBuildSource := ReloadOnSourceChanges

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "2.1.24",
  "dev.zio" %% "zio-http" % "3.7.4",
  "dev.zio" %% "zio-http-datastar-sdk" % "3.7.4",
  "dev.zio" %% "zio-schema" % "1.7.6",
  "dev.zio" %% "zio-schema-derivation" % "1.7.6"
  )

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation"
)

