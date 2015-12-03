import twitter4j.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.util.*;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Twitter {
    public static void main(String[] args) {
        // local vars
        Scanner reader = new Scanner(System.in);
        int number_of_tweets = 0, number_of_search_terms = 0, search_term = 1;

            /* Ask for number of search terms */
        do {
                System.out.println("1 or 2 search terms?");
                System.out.print("> ");
                if (reader.hasNextInt()) {
                    number_of_search_terms = reader.nextInt();
                    System.out.println();
                } else {
                    System.out.println("Error -- Usage: [integer] greater than 0\n");
                }

                reader.nextLine();
        } while (number_of_search_terms < 1 || number_of_search_terms > 2);

        String[] searchTerms = new String[number_of_search_terms];
        int[] numberOfTweets = new int[number_of_search_terms];
        /* master loop, collecting tweets for each search term */
        for (int i = 0; i < number_of_search_terms; i++) {
        /* Ask for search term */
            do {
                System.out.println("search term " + search_term++ + "?");
                System.out.print("> ");
                searchTerms[i] = reader.nextLine().toLowerCase().trim();
                System.out.println();
                if (!searchTerms[i].equals("")) break;
                else System.out.println("Error -- Usage: [String] anything really... come on.\n");
            } while (searchTerms[i].equals(""));

            /* Get number of Tweets */
            do {
                System.out.println("# of tweets to catch? ( min 10 )");
                System.out.print("> ");
                if (reader.hasNextInt()) {
                    numberOfTweets[i] = reader.nextInt();
                    System.out.println();
                } else {
                    System.out.println("Error -- Usage: [integer] greater than 10\n");
                }

                reader.nextLine();
            } while (numberOfTweets[i] < 10);
        }

        for (int i = 0; i < number_of_search_terms; i++) { //For each search term, create a new Streaming object and run it.
            System.out.println("Starting search for: " + searchTerms[i]);
            Streaming streaming = new Streaming(searchTerms[i], numberOfTweets[i]);
            streaming.Start();
        }
    }

    /* Handles all connections to the Twitter Streaming API */
    static class Streaming {
        // local vars
        LinkedHashSet<String> storeTweets = new LinkedHashSet<>();
        LinkedHashSet<String> originalTweets = new LinkedHashSet<>();
        String given;
        int number_of_tweets;
        int total_tweets = 0;

        /* constructor */
        Streaming(String term, int numTweets) {
            given = term;
            number_of_tweets = numTweets;
        }

        public void Start() {
            try
            {
               final int final_tweets = this.number_of_tweets;

                /* Open stream */
                TwitterStream twitterStream = new TwitterStreamFactory().getInstance();

                //Implement a listener
                StatusListener listener = new StatusListener() {
                    int counter = 1;
                    boolean finished = false;

                    @Override
                    public void onStatus(Status status) {
                        boolean stream_status = stream_watcher(final_tweets);

                        /*Print tweet to stream if we are under the cap */
                        if (stream_status) { //If we have reached the tweet limit...
                            if (!finished) { //If this hasn't already happened.
                                finished = true;
                                twitterStream.clearListeners(); //Remove the listeners from our twitterStream object.
                                ClusterAndSave(storeTweets, given);
                                twitterStream.cleanUp();
                            }
                        }
                        else {
                            String tweet = status.getText();
                            originalTweets.add(tweet);

                            if (status.isRetweet()) //If this is a re-tweet, get the original tweet (To avoid possible loss of characters on the end)
                                tweet = status.getRetweetedStatus().getText();

                            tweet = CleanTweet(tweet);
                            storeTweets.add(tweet);
                            System.out.println(given + ": " + counter);
                            // print_tweet(counter, status); //Verbose for user
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

                /* Create filter for english & Search Term */
                FilterQuery stream_Filter = new FilterQuery(given);
                stream_Filter.language("en");                           //English only
                twitterStream.filter(stream_Filter);                    //apply filter to stream

            } catch (Exception e) {
                System.out.println(e);
            }
        }

        private String CleanTweet(String rawTweet) {
            rawTweet = rawTweet.toLowerCase();
            String[] words = rawTweet.split("\\s+"); //Split the tweet into an array of strings.
            String cleanedTweet = "";

            for (int i = 0; i < words.length; i++) {
                boolean includeThisWord = true;
                if (i == 0 && words[i].equals("rt")) //Don't include word that specifies re-tweets.
                    includeThisWord = false;
                else if (words[i].indexOf("@") == 0) //Don't include any user names.
                    includeThisWord = false;
                else if (words[i].indexOf("http://") == 0 || words[i].indexOf("https://") == 0) //Don't include links.
                    includeThisWord = false;

                words[i] = words[i].replaceAll("[^a-zA-Z0-9]", ""); //Remove all non alphanumeric characters.

                if (words[i].length() == 0)
                    includeThisWord = false;

                if (includeThisWord) {
                    cleanedTweet += words[i] + " "; //Add this word to the cleaned tweet.
                }
            }

            if (cleanedTweet.length() > 0)
                cleanedTweet = cleanedTweet.substring(0, cleanedTweet.length() - 1); //Remove last character (which is a space).

            return cleanedTweet;
        }

        /* Used to print a user and their tweet*/
        /*private void print_tweet(int index, Status s) {
            System.out.println("\n[" + index + "]");
            System.out.println("User: " + s.getUser().getScreenName() + ", Tweet: " + s.getText());
        }*/

        private void ClusterAndSave(LinkedHashSet<String> tweetSet, String searchTerm) {
            StringBuffer jsonBuffer = new StringBuffer();
            jsonBuffer.append("{\"type\":\"pre-sentenced\",\"text\":[");

            for (String twt : tweetSet) //For every string in the set of tweet strings, append it to our jsonBuffer.
                jsonBuffer.append("{\"sentence\":\"" + twt + "\"},");

            //If the last character is a comma, remove it.
            if (jsonBuffer.charAt(jsonBuffer.length() - 1) == ',') {
                jsonBuffer.deleteCharAt(jsonBuffer.length() - 1);
            }

            jsonBuffer.append("]}");

            try {
                URL postURL = new URL("https://rxnlp-core.p.mashape.com/generateClusters");
                HttpsURLConnection conn = (HttpsURLConnection) postURL.openConnection();

                //Set request method and headers.
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-Mashape-Key", "3qhKsAzciTmsh2xJmNjMWclaHDuDp1vcvYBjsnpMkIIbebgLRx");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                try (DataOutputStream writer = new DataOutputStream(conn.getOutputStream())) {
                    writer.writeBytes(jsonBuffer.toString()); //Send POST request with our JSON string as the parameter.
                    writer.flush();
                }
                catch (IOException e) {
                    e.printStackTrace(System.out);
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    System.out.println("For search term: " + searchTerm + ". Unique Sentences: " + tweetSet.size());
                    System.out.println(jsonBuffer.toString());

                    while ((line = reader.readLine()) != null)
                        System.out.println(line);

                } catch (IOException e) {
                    e.printStackTrace(System.out);
                    System.out.println(conn.getHeaderField("Connection"));
                    System.out.println(conn.getHeaderField("Content-Length"));
                }
            } catch (Exception e) {
                System.out.println("Error: " + e);
            }
        }

        /* Needed to check if stream is below threshold */

        boolean stream_watcher( int max_tweets ) {
            return ++total_tweets > max_tweets; //Return true if we have reached max tweets amount
        }
    }
}
