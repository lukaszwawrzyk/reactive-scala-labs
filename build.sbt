name := "zad1"

version := "1.0"

scalaVersion := "2.11.8"

resolvers += Resolver.jcenterRepo

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.11",
  "com.typesafe.akka" %% "akka-persistence" % "2.4.11",
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.3.12",
  "com.github.michaelpisula" %% "akka-persistence-inmemory" % "0.2.1",
  "pl.project13.scala" %% "akka-persistence-hbase" % "0.4.0",
  "com.github.scullxbones" %% "akka-persistence-mongo-casbah" % "1.3.5",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.11" % Test,
  "org.scalatest" %% "scalatest" % "3.0.0" % Test,
  "org.scalactic" %% "scalactic" % "3.0.0" % Test,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.3.0" % Test
)

scalacOptions += "-deprecation"