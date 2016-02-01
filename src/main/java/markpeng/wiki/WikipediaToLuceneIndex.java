package markpeng.wiki;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.xml.sax.SAXException;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiXMLParser;

public class WikipediaToLuceneIndex implements IArticleFilter {

	private int currentId = 0;
	public IndexWriter indexWriter = null;

	private List<String> keywords = new ArrayList<String>();

	public WikipediaToLuceneIndex(String luceneFolderPath, String keywordsPath) throws IOException {
		readKeywords(keywordsPath);

		Directory indexDir = FSDirectory.open(new File(luceneFolderPath));
		Analyzer analyzer = new Analyzer() {
			@Override
			protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
				StandardTokenizer tokenzier = new StandardTokenizer(reader);
				TokenStream ts = new StopFilter(tokenzier, StandardAnalyzer.STOP_WORDS_SET);
				int flags = WordDelimiterFilter.SPLIT_ON_NUMERICS | WordDelimiterFilter.SPLIT_ON_CASE_CHANGE
						| WordDelimiterFilter.GENERATE_NUMBER_PARTS | WordDelimiterFilter.GENERATE_WORD_PARTS;
				ts = new WordDelimiterFilter(ts, flags, null);
				ts = new LowerCaseFilter(ts);
				ts = new PorterStemFilter(ts);

				return new TokenStreamComponents(tokenzier, ts);
			}
		};

		IndexWriterConfig config = new IndexWriterConfig(Version.LATEST, analyzer);
		indexWriter = new IndexWriter(indexDir, config);
	}

	@Override
	public void process(WikiArticle page, Siteinfo info) throws SAXException {
		// System.out.println("----------------------------------------");
		// System.out.println(page.getTitle());
		// System.out.println("----------------------------------------");
		// System.out.println(page.getText());

		try {
			if (containKeyword(page.getText()))
				index(page.getTitle(), page.getText());
			else
				System.out.println("Skip: " + page.getTitle());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void addDoc(String title, String text) throws IOException {
		Document doc = new Document();
		doc.add(new IntField("id", currentId, Field.Store.YES));
		doc.add(new TextField("title", title, Field.Store.YES));
		doc.add(new TextField("text", text, Field.Store.YES));
		indexWriter.addDocument(doc);

		currentId++;
	}

	public void index(String title, String text) throws IOException {
		System.out.println("----------------------------------------");
		System.out.println("Indexing " + title + " (text size: " + text.length() + ") ......");
		addDoc(title, text);
		System.out.println("Current size:" + currentId);
	}

	private void readKeywords(String keywordsPath) {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(keywordsPath)));
			String aLine;
			while ((aLine = reader.readLine()) != null) {
				if (aLine.trim().length() > 0) {
					if (!keywords.contains(aLine))
						keywords.add(aLine);
				}
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean containKeyword(String text) {
		for (String keyword : keywords) {
			if (text.contains(keyword))
				return true;
		}

		return false;
	}

	public static void main(String[] args) throws IOException {
		// java -cp lucene-wikipedia-0.0.1-jar-with-dependencies.jar
		// markpeng.wiki.WikipediaToLuceneIndex
		// enwiki-latest-pages-articles.xml.bz2 "lucene-wiki-index-keywords" "keywords.txt"

		if (args.length != 3) {
			System.err.println("Usage: java -cp lucene-wikipedia-0.0.1-jar-with-dependencies.jar "
					+ "markpeng.wiki.WikipediaToLuceneIndex <path of XML bz2 file> " + "<path of lucene index folder> "
					+ "<keywords to filter>");
			System.exit(-1);
		}

		String bz2Filename = args[0];
		String luceneFolderPath = args[1];
		String keywordsPath = args[2];

		WikipediaToLuceneIndex handler = new WikipediaToLuceneIndex(luceneFolderPath, keywordsPath);
		try {
			WikiXMLParser wxp = new WikiXMLParser(bz2Filename, handler);
			wxp.parse();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			handler.indexWriter.close();
		}
	}

}
