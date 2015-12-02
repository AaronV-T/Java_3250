import twitter4j.*;
import java.util.*;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.HttpResponse;

public class Twitter {

    /* Main */
    public static void main(String[] args) {
        Streaming streamer = new Streaming();
        TreeSet<String> storeTweets = new TreeSet<>();
        try {
            //Try to open stream using the streamer class
            streamer.streaming(storeTweets);
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }

    /* Handles all connections to the Twitter Streaming API */
    static class Streaming {

        public void streaming(TreeSet<String> storeTweets) {

            Scanner reader = new Scanner(System.in);
            String given         = "";                                  //For search term
            int number_of_tweets = 0;                                   //Total number of tweets, default at 25

            try
            {
                /* Ask for search term */
                while(given.equals("")) {
                    System.out.println("search term?");
                    System.out.print("> "); given = reader.nextLine();

                    System.out.println();
                }

                given = given.toLowerCase();
                given = given.trim();

                /*Get number of Tweets*/
                while(number_of_tweets == 0) {
                    System.out.println("# of tweets to catch?");
                    System.out.print("> "); number_of_tweets = reader.nextInt();
                    System.out.println();
                }

                final int final_tweets = number_of_tweets;              //Inner class needs a final variable

                /* Open stream */
                TwitterStream twitterStream = new TwitterStreamFactory().getInstance();

                //Implement a listener
                StatusListener listener = new StatusListener() {
                    public int counter = 1;

                    @Override
                    public void onStatus(Status status) {
                        boolean stream_status = stream_watcher(final_tweets);

                        /*Print tweet to stream if we are under the cap */
                        if (stream_status) {
                            twitterStream.clearListeners();       //reached cap, shutdown stream
                            toJson(storeTweets);
                            twitterStream.shutdown();
                        }
                        else {
                            print_tweet(counter++, status,storeTweets);
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

        /* Used to print a user and their tweet */
        private static void print_tweet(int index, Status s,TreeSet theSet) {
            String tweet = s.getText().toLowerCase();
            if (index > 0)
            //    System.out.println("\n[" + index + "]");
            //System.out.println(s.getUser().getName() + " @" + s.getUser().getScreenName() + "Tweet: " + tweet);
            theSet.add(s.getUser().getName() + "@" + s.getUser().getScreenName() + "Tweet" + tweet);
        }

        private static void toJson(TreeSet<String> theSet){
            try{
                String jsonQuery = "{\"type\": \"pre-sentenced\",\"text\": [";
                Iterator<String> it = theSet.iterator();
                for(String twt:theSet){
                    jsonQuery+="{\"sentence\": \""+twt+"\"},";
                }
                jsonQuery+="]}";
                System.out.println(jsonQuery);
            }catch(Exception e){
                e.printStackTrace(System.out);
            }
        }

        /* Needed to check if stream is below threshold */
        static int total_tweets = 0;
        static boolean stream_watcher( int max_tweets ) {
            return ++total_tweets > max_tweets;
        }

    }
}
