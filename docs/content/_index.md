+++
title = "Kannich"
description = "The CI pipeline that doesn't suck."
template = "landing.html"

[extra.hero]
title = "Kannich"
description = "A simple and powerful way to build CI pipelines that work on your machine and in any CI system."

[[extra.hero.cta_buttons]]
text = "Get Started"
url = "/docs/quick-start/"
style = "primary"

[[extra.hero.cta_buttons]]
text = "View on GitHub"
url = "https://github.com/derkork/kannich"
style = "secondary"

[extra.features_section]
title = "Key Features"
description = "Build and test your CI pipelines locally before pushing to remote servers!"

[[extra.features]]
icon = "rocket"
title = "Works Everywhere"
desc = "You can design CI pipelines on your local machine and test them without having to round-trip to a CI system. Kannich works with any CI system that can launch docker containers, or no CI system at all."

[[extra.features]]
icon = "code"
title = "Powerful scripting"
desc = "Pipelines are written in KotlinScript that allows you to sidestep the YAML/shellscript hell. You have access to the full JVM ecosystem."

[[extra.features]]
icon = "box"
title = "Container-Based"
desc = "Kannich pipelines are executed in an isolated container, nothing is installed or changed on your machine."

[[extra.features]]
icon = "database"
title = "Caching"
desc = "Kannich comes with built-in caching for downloads and dependencies without any additional configuration."
+++
