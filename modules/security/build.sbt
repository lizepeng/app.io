name := "app-io.security"

Common.settings

scalacOptions += "-feature"

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false