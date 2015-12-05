import twitter4j.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.util.*;
import java.net.URL;
import org.json.XML;
import org.json.JSONObject;

public class Twitter {
    public static void main(String[] args) {
        /* local vars */
        String[] searchTerms;
        int[] numberOfTweets;
        int numberOfSearchTerms, searchTerm = 1, minNumOfTweets = 10; /* change minNumOfTweets HERE!!! */

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
                    System.out.println("# of tweets to catch? ( min " + minNumOfTweets + " )");
                    System.out.print("> ");
                    if (reader.hasNextInt()) {
                        numberOfTweets[i] = reader.nextInt();
                        if (numberOfTweets[i] >= minNumOfTweets) {
                            reader.nextLine();
                            System.out.println();
                            break;
                        }
                    } else {
                        reader.next(); /* account for string as input */
                    }
                    System.out.println("Error -- Usage: [integer: >= " + minNumOfTweets + "]\n");
                } while (true);
            }
        }

        /* GUI -- args provided */
        else {
            /* args [String 1] [tweet count 1] [String 2] [Tweet count 2] */
            if(args.length == 2){
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
        }

        /* for each search term, create a new Streaming object and run it */
        for (int i = 0; i < numberOfSearchTerms; i++) {
            System.out.println("Starting search for: " + searchTerms[i]);
            Streaming streaming = new Streaming(searchTerms[i], numberOfTweets[i]);
            streaming.Start();
        }
    }

    /* handles all connections to the Twitter Streaming API */
    static class Streaming {
        /* local vars */
        LinkedHashSet<String> storeTweets = new LinkedHashSet<>();
        LinkedHashSet<String> originalTweets = new LinkedHashSet<>();
        int number_of_tweets, total_tweets = 0;
        String given;

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
                                ClusterAndSave(storeTweets, given);
                                twitterStream.cleanUp();
                            }
                        }
                        else {
                            String tweet = status.getText();
                            originalTweets.add(tweet);

                            if (status.isRetweet()) /* if this is a re-tweet, get the original tweet (To avoid possible loss of characters on the end) */
                                tweet = status.getRetweetedStatus().getText();

                            tweet = CleanTweet(tweet);
                            storeTweets.add(tweet);

                            System.out.println(given + ": " + counter); /* print_tweet(counter, status); verbose for user */
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

        /* method used to clean up the tweet as it streams in from the API
           returns: String cleanedTweet */
        private String CleanTweet(String rawTweet) {
            rawTweet = rawTweet.toLowerCase();
            String[] words = rawTweet.split("\\s+"); /* split the tweet into an array of strings */
            String cleanedTweet = "";

            for (int i = 0; i < words.length; i++) {
                boolean includeThisWord = true;
                if (i == 0 && words[i].equals("rt")) /* don't include word that specifies re-tweets */
                    includeThisWord = false;
                else if (words[i].indexOf("@") == 0) /* don't include any user names */
                    includeThisWord = false;
                else if (words[i].indexOf("http://") == 0 || words[i].indexOf("https://") == 0) /* Don't include links */
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

        /* ****** Aaron's method he is still making changes to ****** */
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
                    System.out.println("\nFor search term: " + searchTerm + ". Unique Sentences: " + tweetSet.size());
                    System.out.println(jsonBuffer.toString());

                    while ((line = reader.readLine()) != null)
                        jToXml(line);

                } catch (IOException e) {
                    e.printStackTrace(System.out);
                    System.out.println(conn.getHeaderField("Connection"));
                    System.out.println(conn.getHeaderField("Content-Length"));
                }
            } catch (Exception e) {
                System.out.println("Error: " + e);
            }
        }
        private void jToXml(String input){
            try {
                JSONObject myJson = new JSONObject(input);
                String xmlCon = XML.toString(myJson);
                System.out.println(xmlCon);
                try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(given +".xml"), "utf-8"))) {
                    writer.write(xmlCon);
                }catch(Exception e){
                    e.printStackTrace(System.out);
                }
            }catch(Exception e){
                e.printStackTrace(System.out);
            }
        }

        /* needed to check if stream is below threshold */
        boolean stream_watcher( int max_tweets ) {
            return ++total_tweets > max_tweets; /* return true if we have reached max tweets amount */
        }
    }
}
