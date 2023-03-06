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
make sure to use at least Java 19. A JDK archive can be downloaded and unpacked from https://jdk.java.net

    $ export JAVA_HOME=<path to jdk>
    $ export PATH=$JAVA_HOME/bin:$PATH

    $ java -version
    openjdk version "19.0.2" 2023-01-17
    OpenJDK Runtime Environment (build 19.0.2+7-44)
    OpenJDK 64-Bit Server VM (build 19.0.2+7-44, mixed mode, sharing)

    $ java build/Build.java


## Run

    $ out/runtime/bin/teamcheck

