name := "zad1"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.11",
  "org.typelevel" %% "cats" % "0.7.2",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.11" % Test,
  "org.scalatest" %% "scalatest" % "3.0.0" % Test
)
