# Team Check

A Lichess Team Management application

# Download

Download the application for your Operating System, unpack and run (`teamcheck-x.y.z/bin/teamcheck`)

https://github.com/tors42/teamcheck/releases/

# Screenshots

Use search field to find a team, and select it in drop-down box,

![search](https://user-images.githubusercontent.com/4084220/131161893-a0b8a407-2043-4fb3-9012-50fb6cbf1871.png)

And click `Launch` to see team members "flying" in...

![launch](https://user-images.githubusercontent.com/4084220/131161888-15768f3d-f651-4b4f-90c6-7d65b85db4aa.png)


# Build

If you want to build the application yourself,
make sure to use at least Java 21. A JDK archive can be downloaded and unpacked from https://jdk.java.net

    $ java -version
    openjdk version "22.0.1" 2024-04-16
    OpenJDK Runtime Environment (build 22.0.1+8-16)
    OpenJDK 64-Bit Server VM (build 22.0.1+8-16, mixed mode, sharing)

    $ java build/Build.java

or to cross-compile:

    $ java build/Build.java cross

## Run

    $ out/runtime/bin/teamcheck

