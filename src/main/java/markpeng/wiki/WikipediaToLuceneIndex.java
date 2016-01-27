package markpeng.wiki;

import org.xml.sax.SAXException;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiXMLParser;

public class WikipediaToLuceneIndex {

	static class ArticleFilter implements IArticleFilter {
		@Override
		public void process(WikiArticle page, Siteinfo info) throws SAXException {
			System.out.println("----------------------------------------");
			System.out.println(page.getTitle());
			System.out.println("----------------------------------------");
			System.out.println(page.getText());
		}

	}

	public static void main(String[] args) {
		// java -cp lucene-wikipedia-0.0.1-jar-with-dependencies.jar
		// markpeng.wiki.WikipediaToLuceneIndex
		// enwiki-latest-pages-articles.xml.bz2

		if (args.length != 1) {
			System.err.println("Usage: Wikipedia Parser <path of XML bz2 file>");
			System.exit(-1);
		}
		// Example:
		String bz2Filename = args[0];
		try {
			IArticleFilter handler = new ArticleFilter();
			WikiXMLParser wxp = new WikiXMLParser(bz2Filename, handler);
			wxp.parse();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
