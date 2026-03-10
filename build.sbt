name := "zio-http-datastar-chat"

version := "0.1.0"

scalaVersion := "3.7.4"

Global / onChangedBuildSource := ReloadOnSourceChanges

libraryDependencies ++= Seq(
  "dev.zio"                   %% "zio"                   % "2.1.24",
  "dev.zio"                   %% "zio-http"              % "3.9.0+16-60270509-SNAPSHOT",
  "dev.zio"                   %% "zio-http-datastar-sdk" % "3.9.0+16-60270509-SNAPSHOT",
  "dev.zio"                   %% "zio-schema"            % "1.8.2",
  "dev.zio"                   %% "zio-schema-derivation" % "1.8.2",
  "com.aayushatharva.brotli4j" % "brotli4j"              % "1.20.0",
)

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
)
