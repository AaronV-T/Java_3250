import twitter4j.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import org.json.XML;
import org.json.JSONObject;

public class Twitter {
    public static void main(String[] args) {
        /* local vars */
        String[] searchTerms = new String[0];
        int[] numberOfTweets = new int[0];
        int numberOfSearchTerms = 0, searchTerm = 1, maxTweets = 10000, minNumOfTweets = 10; /* change minNumOfTweets HERE!!! */

        /* terminal -- no GUI */
        if(args.length == 0){
            Scanner reader = new Scanner(System.in);
            /* ask for number of search terms */
            do {
                System.out.println("1 or 2 search terms?");
                System.out.print("> ");
                if (reader.hasNextInt()) {
                    numberOfSearchTerms = reader.nextInt();
                    if (numberOfSearchTerms > 0 && numberOfSearchTerms <= 2) {
                        reader.nextLine();
                        System.out.println();
                        break;
                    }
                } else {
                    reader.next(); /* account for string as input */
                }
                System.out.println("Error -- Usage: [integer: 1 | 2]\n");
            } while (true);
            
            searchTerms = new String[numberOfSearchTerms];
            numberOfTweets = new int[numberOfSearchTerms];
            
            /* master loop, collecting tweets for each search term */
            for (int i = 0; i < numberOfSearchTerms; i++) {
            /* ask for search term */
                do {
                    System.out.println("search term " + searchTerm + "?");
                    System.out.print("> ");
                    searchTerms[i] = reader.nextLine().toLowerCase().trim();
                    if (!searchTerms[i].equals("")) {
                        System.out.println();
                        searchTerm++;
                        break;
                    }
                    System.out.println("Error -- Usage: [String: anything really... come on]\n");
                } while (searchTerms[i].equals(""));

            /* get number of Tweets */
                do {
                    System.out.println("# of tweets to catch? (Min: " + minNumOfTweets + ". Max: " + maxTweets + ")");
                    System.out.print("> ");
                    if (reader.hasNextInt()) {
                        numberOfTweets[i] = reader.nextInt();
                        if (numberOfTweets[i] >= minNumOfTweets && numberOfTweets[i] <= maxTweets) {
                            reader.nextLine();
                            System.out.println();
                            break;
                        }
                    } else {
                        reader.next(); /* account for string as input */
                    }
                    System.out.println("Error -- Usage: [" + minNumOfTweets + " <= VALUE <= " + maxTweets + "]\n");
                } while (true);
            }
        }

        /* GUI -- args provided */
        else {
            /* args [String 1] [tweet count 1] [String 2] [Tweet count 2] */
            try {
                if (args.length == 2) {
                /* One search */
                    numberOfSearchTerms = 1;
                    numberOfTweets = new int[1];
                    numberOfTweets[0] = Integer.parseInt(args[1]);
                    searchTerms = new String[1];
                    searchTerms[0] = args[0];
                } else {
                    numberOfSearchTerms = 2;
                    numberOfTweets = new int[2];
                    numberOfTweets[0] = Integer.parseInt(args[1]);
                    numberOfTweets[1] = Integer.parseInt(args[3]);
                    searchTerms = new String[2];
                    searchTerms[0] = args[0];
                    searchTerms[1] = args[2];
                }
            } catch (NumberFormatException e) {
                printBadArgumentMessageAndExit();
            }

            for (int i = 0; i < numberOfSearchTerms; i++) { /* if the tweet numbers aren't in the correct range... */
                if (numberOfTweets[i] < 10 || numberOfTweets[i] > 10000)
                    printBadArgumentMessageAndExit();
            }

            for (int i = 0; i < searchTerms.length; i++)  /* for each search term, set all letters to lowercase, replace all carets with spaces, and trim whitespace from ends */
                searchTerms[i] = searchTerms[i].toLowerCase().replace("^", " ").trim();
        }

        /* for each search term, create a new Streaming object and run it */
        for (int i = 0; i < numberOfSearchTerms; i++) {
            System.out.println("Starting search for: " + searchTerms[i]);
            Streaming streaming = new Streaming(searchTerms[i], numberOfTweets[i]);
            streaming.Start();
        }
    }

    static void printBadArgumentMessageAndExit() {
        System.out.println("Your arguments are invalid. Please use the following format:");
        System.out.println("java -jar 3250_Final.jar (term1) (integer) (term2) (integer)");
        System.exit(1);
    }

    /* handles all connections to the Twitter Streaming API */
    static class Streaming {
        /* local vars */
        LinkedHashSet<String> storeTweets = new LinkedHashSet<>(); /* storage of clean tweets */
        LinkedHashSet<String> originalTweets = new LinkedHashSet<>(); /* storage of original tweets */
        int number_of_tweets, total_tweets = 0;
        String given; /* search term */

        /* constructor */
        Streaming(String term, int numTweets) {
            given = term;
            number_of_tweets = numTweets;
        }

        /* method used to open twitter4j api, and collect tweets */
        public void Start() {
            try
            {
                final int final_tweets = this.number_of_tweets;

                /* open stream */
                TwitterStream twitterStream = new TwitterStreamFactory().getInstance();

                /* implement a listener */
                StatusListener listener = new StatusListener() {
                    int counter = 1;
                    boolean finished = false;

                    @Override
                    public void onStatus(Status status) {
                        boolean stream_status = stream_watcher(final_tweets);

                        /* print tweet to stream if we are under the cap */
                        if (stream_status) { /* if we have reached the tweet limit... */
                            if (!finished) { /* if this hasn't already happened */
                                finished = true;
                                twitterStream.clearListeners(); /* remove the listeners from our twitterStream object */

                                String clusterResults = getClusterResults(storeTweets, given); /* get cluster results for our collected tweets */
                                if (clusterResults.equals("fail"))
                                    System.out.println("There was an issue with retrieving cluster data, aborting.");
                                else
                                    saveAsXML(replaceTweetsWithOriginals(clusterResults)); /* replace clean tweets with originals, convert to XML and write to file */

                                twitterStream.cleanUp(); /* shutdown internal stream consuming thread */
                            }
                        }
                        else {
                            String tweet = status.getText();

                            if (status.isRetweet()) /* if this is a re-tweet, get the original tweet (To avoid possible loss of characters on the end) */
                                tweet = status.getRetweetedStatus().getText();

                            String cleanTweet = cleanTweet(tweet);
                            if (!storeTweets.contains(cleanTweet)) { /* if this is not a duplicate tweet, add it to original tweet set and add its clean tweet to to the clean tweet set */
                                String safeTweet = removeUnsafeUnicodeCharacters(tweet);
                                originalTweets.add("@" + status.getUser().getScreenName() + "= " + safeTweet);
                                storeTweets.add(cleanTweet);
                            }
                            System.out.println(given + ": " + counter);
                            counter++;
                        }
                    }

                    @Override
                    public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
                        //System.out.println("Got a status deletion notice id:" + statusDeletionNotice.getStatusId());
                    }

                    @Override
                    public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
                        System.out.println("Got track limitation notice:" + numberOfLimitedStatuses);
                    }

                    @Override
                    public void onScrubGeo(long userId, long upToStatusId) {
                        System.out.println("Got scrub_geo event userId:" + userId + " upToStatusId:" + upToStatusId);
                    }

                    @Override
                    public void onStallWarning(StallWarning warning) {
                        System.out.println("Got stall warning:" + warning);
                    }

                    @Override
                    public void onException(Exception ex) {
                        ex.printStackTrace();
                    }
                };

                /* add our listener to the stream */
                twitterStream.addListener(listener);

                /* create filter for english & Search Term */
                FilterQuery stream_Filter = new FilterQuery(given);
                stream_Filter.language("en");                           /* english only */
                twitterStream.filter(stream_Filter);                    /* apply filter to stream */

            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        /* cleanTweet: Cleans and returns a string in a format that will allow for good results from the clustering API. */
        private String cleanTweet (String rawTweet) {
            rawTweet = rawTweet.toLowerCase();
            String[] words = rawTweet.split("\\s+"); /* split the tweet into an array of strings */
            String cleanedTweet = "";

            for (int i = 0; i < words.length; i++) { /* for every word in the String */
                boolean includeThisWord = true;
                if (words[i].equals("rt")) /* don't include word that specifies re-tweets */
                    includeThisWord = false;
                else if (words[i].indexOf("@") == 0) /* don't include any user names */
                    includeThisWord = false;
                else if (words[i].indexOf("http://") == 0 || words[i].indexOf("https://") == 0) /* don't include links */
                    includeThisWord = false;

                words[i] = words[i].replaceAll("[^a-zA-Z0-9]", ""); /* remove all non alphanumeric characters */

                if (words[i].length() == 0)
                    includeThisWord = false;

                if (includeThisWord) {
                    cleanedTweet += words[i] + " "; /* add this word to the cleaned tweet */
                }
            }

            if (cleanedTweet.length() > 0)
                cleanedTweet = cleanedTweet.substring(0, cleanedTweet.length() - 1); /* remove last character (which is a space) */

            return cleanedTweet;
        }

        /* getClusterResults: Formats all strings in a LinkedHashSet into JSON, calls method to get cluster data and returns it.  */
        String getClusterResults (LinkedHashSet<String> tweetSet, String searchTerm) {
            StringBuilder jsonBuffer = new StringBuilder();
            jsonBuffer.append("{\"type\":\"pre-sentenced\",\"text\":[");

            for (String twt : tweetSet) /* for every string in the set of tweet strings, append it to our jsonBuffer */
                jsonBuffer.append("{\"sentence\":\"" + twt + "\"},");

            if (jsonBuffer.charAt(jsonBuffer.length() - 1) == ',') {  /* if the last character is a comma, remove it */
                jsonBuffer.deleteCharAt(jsonBuffer.length() - 1);
            }

            jsonBuffer.append("]}");

            String clusterResults = "";
            try { /* try to use the clustering API with the first URL */
                URL postURL = new URL("https://rxnlp-core.p.mashape.com/generateClusters");
                HttpsURLConnection conn = (HttpsURLConnection) postURL.openConnection();
                clusterResults = getClusterResultsConnection(conn, jsonBuffer);
                System.out.println("Cluster results received for search term: " + searchTerm + ". Unique Sentences: " + tweetSet.size());
            } catch (Exception e) { /* if there was a problem, try to use the clustering API with the 2nd URL */
                System.out.println("There was a problem when trying with first URL, attempting with second URL.");
                try {
                    URL postURL = new URL("http://findilike.linkpc.net:9000/generateClusters");
                    HttpURLConnection conn = (HttpURLConnection) postURL.openConnection();
                    clusterResults = getClusterResultsConnection(conn, jsonBuffer);
                    System.out.println("Cluster results received for search term: " + searchTerm + ". Unique Sentences: " + tweetSet.size());
                } catch (Exception e2) {
                    writeExceptionsToFile(e, e2);
                    clusterResults = "fail";
                }
            }

            return clusterResults;
        }

        /* getClusterResultsConnection: Connects and submits POST request to given HTTP connection, returns the response. */
        String getClusterResultsConnection (HttpURLConnection conn, StringBuilder jsonBuffer) throws Exception {
            /* set request method and headers */
            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-Mashape-Key", "3qhKsAzciTmsh2xJmNjMWclaHDuDp1vcvYBjsnpMkIIbebgLRx");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            try (DataOutputStream writer = new DataOutputStream(conn.getOutputStream())) {
                writer.writeBytes(jsonBuffer.toString()); /* send POST request with our JSON-formatted string as the parameter */
                writer.flush();
            }
            catch (IOException e) {
                throw e;
            }
            System.out.println("Tweets sent for analysis, waiting for response.");

            String clusterResults = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    clusterResults += line;
                }
            } catch (IOException e) {
                throw e;
            }

            return clusterResults;
        }

        /* getClusterResultsConnection: Connects and submits POST request to given HTTPS connection, returns the response. */
        String getClusterResultsConnection (HttpsURLConnection conn, StringBuilder jsonBuffer) throws Exception {
            /* set request method and headers */
            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-Mashape-Key", "3qhKsAzciTmsh2xJmNjMWclaHDuDp1vcvYBjsnpMkIIbebgLRx");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            try (DataOutputStream writer = new DataOutputStream(conn.getOutputStream())) {
                writer.writeBytes(jsonBuffer.toString()); /* send POST request with our JSON-formatted string as the parameter */
                writer.flush();
            }
            catch (IOException e) {
                throw e;
            }
            System.out.println("Tweets sent for analysis, waiting for response.");

            String clusterResults = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    clusterResults += line;
                }
            } catch (IOException e) {
                throw e;
            }

            return clusterResults;
        }

        /* removeUnsafeUnicodeCharacters: Removes characters that cause problems in Java Strings. */
        String removeUnsafeUnicodeCharacters (String unSanitized) {
            StringBuilder sanitized = new StringBuilder();

            for (int i = 0; i < unSanitized.length(); i++) {
                boolean useThisChar = true;
                int unicodeVal = unSanitized.charAt(i);

                if (unicodeVal == 13) /* if character is a carriage return, do not use */
                    useThisChar = false;

                if (useThisChar) /* if this character is usable, append it to the StringBuilder */
                    sanitized.append(unSanitized.charAt(i));
            }

            return sanitized.toString();
        }

        /* replaceTweetsWithOriginals: Replaces all the cleaned tweets in the JSON cluster results with the original tweets. */
        String replaceTweetsWithOriginals (String jsonResults) {
            System.out.println("Replacing cleaned tweets with originals.");

            for (String origTweet: originalTweets) { /* for every string in the set of original tweet strings, replace the clean text with the original tweet text */
                String cleanedTweet = cleanTweet(origTweet); /* get its clean tweet for comparison */

                String escapedOrigTweet = origTweet;
                escapedOrigTweet = escapedOrigTweet.replace("\\", "\\\\"); /* escape backslashes */
                escapedOrigTweet = escapedOrigTweet.replace("\n", " "); /* replace new line characters with spaces */
                //escapedOrigTweet = escapedOrigTweet.replace("\n", "\\n"); /* escape new line characters */
                escapedOrigTweet = escapedOrigTweet.replace("\"", "\\\""); /* escape quotation marks */

                int tweetIndex = -1;
                do {
                    if (cleanedTweet.length() > 0) {
                        tweetIndex = jsonResults.indexOf(": " + cleanedTweet + "\""); /* find where this tweet should go in the json string */
                        if (tweetIndex > -1) /* if the text was found, increase index by two to exclude the ": " that was used to find the index */
                            tweetIndex += 2;
                        else { /* else, try to find it again without the space after the colon */
                            tweetIndex = jsonResults.indexOf(":" + cleanedTweet + "\""); /* find where this tweet should go in the json string */
                            if (tweetIndex > -1) /* if the text was found, increase index by 1 to exclude the ":" that was used to find the index */
                                tweetIndex++;
                        }

                        if (tweetIndex > -1) { /* if the text was found, replace the cleaned text from the json string with the escaped original tweet */
                            jsonResults = jsonResults.substring(0, tweetIndex) + escapedOrigTweet + jsonResults.substring(tweetIndex + cleanedTweet.length());
                        }
                    }
                } while (tweetIndex > -1); /* repeat to replace any duplicates that may have been returned by the clustering API */
            }

            return jsonResults;
        }

        /* saveAsXML: Converts JSON to XML and writes to a file */
        void saveAsXML (String input){
            try {
                JSONObject myJson = new JSONObject(input);
                String xmlCon = XML.toString(myJson);

                System.out.println("Writing results in XML to file.");
                try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(given +".xml"), "utf-8"))) {
                    writer.write(xmlCon);
                    System.out.println("Write finished.");
                }catch(Exception e){
                    writeExceptionToFile(e);
                }
            }catch(Exception e){
                writeExceptionToFile(e);
            }
        }

        /* needed to check if stream is below threshold */
        boolean stream_watcher( int max_tweets ) {
            return ++total_tweets > max_tweets; /* return true if we have reached max tweets amount */
        }

        /* writeExceptionToFile: Prints an exception to System.out and writes it to a file. */
        void writeExceptionToFile(Exception excep) {
            excep.printStackTrace(System.out);

            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date();
            String fileName = "exception_" + dateFormat.format(date).trim().replace(" ", "_").replace(":", "-") + ".txt";

            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(fileName), "utf-8"))) {
                writer.write("Exception: " + excep.toString());
                System.out.println("Exception written to file.");
            } catch(Exception e){
                System.out.println("Failed to write exception to file.");
            }
        }

        /* writeExceptionsToFile: Prints two exceptions to System.out and writes them to a file. */
        void writeExceptionsToFile(Exception excep, Exception excep2) {
            excep.printStackTrace(System.out);
            excep2.printStackTrace(System.out);

            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date();
            String fileName = "exception_" + dateFormat.format(date).trim().replace(" ", "_").replace(":", "-") + ".txt";

            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(fileName), "utf-8"))) {
                writer.write("Exception 1: " + excep.toString() + "\n");
                writer.write("Exception 2: " + excep2.toString());
                System.out.println("Exceptions written to file.");
            } catch(Exception e){
                System.out.println("Failed to write exceptions to file.");
            }
        }
    }
}
