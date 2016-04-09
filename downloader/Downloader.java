package downloader;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;

public class Downloader {
	
	static String baseURL = "http://gd2.mlb.com/components/game/mlb";
	static String baseLocalURL;
	static ArrayList<String> files = new ArrayList<String>();
	
	private void print(String s) {
		System.out.println(s);
	}
	
	public static void main(String[] args) {
		Downloader downloader = new Downloader();
		int year;
		if (args.length == 0) {
			baseLocalURL = "C:\\Users\\sunny\\Documents\\saber\\data";
			year = 2007;
		}
		else if (args.length < 2) {
			System.out.println("Invalid number of arguments");
			return;
		}
		else {

			year = Integer.parseInt(args[0]);
			baseLocalURL = args[1];
		}
		if (args.length > 2) {
			for (int i = 2; i < args.length; i++) {
				files.add(args[i]);
			}
		}
		else {
			System.out.println("No filenames specified, using default: game_events.json, boxscore.json, inning_all.xml, inning_hit.xml, inning_Scores.xml");
			files.add("game_events.json");
			files.add("boxscore.json");
			files.add("inning_all.xml");
			files.add("inning_hit.xml");
			files.add("inning_Scores.xml");
		}
		downloader.download(year);
	}
	
	public void download(int year) {
		for(int month = 4 ; month <= 11 ; month++) {
			download(year, month);
		}
	}

	Semaphore s = new Semaphore(8);
	public void download(int year, int month) {
		try {
			ExecutorService executor = Executors.newCachedThreadPool();
			for( int day = 1 ; day <= 31 ; day++) {
				String parentFolder = String.format("%s/year_%02d/month_%02d/day_%02d/",
						baseURL,
						year,
						month,
						day);

				ArrayList<String> gameFolders = new ArrayList<String>();
				try {
					Document doc = Jsoup.connect(parentFolder).post();
					Document parse = Jsoup.parse(doc.html());
					Elements lis = parse.select("li > a");
					for (Element li : lis) {
						String href = li.attr("href");
						if (href.startsWith("gid_")) {
							gameFolders.add(href.replace("/", ""));
						}
					}
				} catch(IOException e) {
					return;
				}
				
				
				for (String gameFolder : gameFolders) {
					String directory = String.format("%s/year_%02d/month_%02d/day_%02d/%s",
							baseURL,
							year,
							month,
							day,
							gameFolder);
					
					for (String filename : files) {
						URL u;
						if (filename.contains("inning_")) {
							
							u = new URL(String.format("%s/%s/%s", directory, "inning", filename));
						}
						else {
							u = new URL(String.format("%s/%s", directory, filename));
						}

						File file = new File(String.format("%s\\%s",baseLocalURL, u.getFile()));
						if (file.exists()) {
							System.out.println(filename + " has already been downloaded");
							continue;
						}
						s.acquire();
						executor.submit(new FileDownloader(u, file));
					}
				}
			}
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	class FileDownloader implements Callable<String> {
		URL url;
		File file;
		FileDownloader(URL u, File f) {
			url = u;
			file = f;
		}

		@Override
		public String call() {
			try {
				FileUtils.copyURLToFile(url, file, 5000, 5000);
				System.out.println(String.format("Downloaded to %s", file.getAbsolutePath()));
			} catch (IOException e) {
				if (file.getName().equalsIgnoreCase("game_events.json") || file.getName().equalsIgnoreCase("boxscore.json")) {
					URL u = null;
					try {
						u = new URL("http://"+url.getHost()+url.getPath().replace("json", "xml"));
						File f = new File(String.format("%s\\%s",baseLocalURL, u.getFile()));
						FileUtils.copyURLToFile(u, f, 5000, 5000);
					} catch (IOException e1) {
						System.out.println(u.getPath() + " does not exist on server ");
						s.release();
						return null;
					}
				}
				else if (file.getName().equalsIgnoreCase("inning_all.xml")) {
					try {
						int i = 1;
						boolean active = true;
						while (active) {
							String sURL = "http://"+url.getHost()+url.getPath().replace("inning_all.xml", "inning_"+i+".xml");
							Connection c = Jsoup.connect(sURL).header("Method", "HEAD");
							if (c.execute().statusCode() == 200) {
								i++;
								URL u = new URL(sURL);
								File f = new File(baseLocalURL + "\\" + u.getFile());
								FileUtils.copyURLToFile(u, f, 5000, 5000);
							}
							else {
								active = false;
							}
						}
					} catch (IOException e1) {
						//We expect this exception to throw since we just count up from 1, and the amount of innings
						//in a game is usually 9 so it will throw when it runs out of innings. This is just to make
						//it easier by not having to figure out how many innings were actually in the game
						s.release();
						return null;
					}
					
				}
				else {
					System.out.println(url.getPath() + " does not exist on server");
				}
				s.release();
				return null;
			}
			s.release();
			return file.getAbsolutePath();
		}
	}
}
