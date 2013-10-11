# README

## INSTALLATION

Install the Play 2.2.0 framework, it is important that this version is used. This
can be done at http://www.playframework.com/. There may also the requirement to
install scala since, like Neo4j, much of it is built in scala.

After installing Play and Scala

Please note that it assumes MongoDB is available at localhost:27017.
If this is not correct it can be changed in utils.mongodb.MongoDatabaseConnection.java.

Neo4j is embedded however it is important that the applicatio is shutdown correctly otherwise the app
may fail to start again. In such an event please delete the entire Neo4j database folder and the seeds/seeds.lock
file which will repopulate the database.

1. Open a command prompt
2. Navigate to the mekong directory (same directory as this README)
3. Enter the command 'play run'
4. The application will start
5. Visit localhost:9000, using a Chrome browser is preferred as Firefox or IE have not
been tested.
6. The database will then proceed to seed and you should see log information about
this as it occurs.
7. The page should load the login page, where you can register a new user and
then proceed to start shopping.

Many of the actions will log information out to the console.

