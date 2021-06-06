import ammonite.ops._
import ammonite.ops.ImplicitWd._
import scala.io.StdIn.readLine

import $ivy.`org.apache.pdfbox:pdfbox:3.0.0-RC1`
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.pdmodel.PDDocumentCatalog
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode

import $ivy.`com.baidu.aip:java-sdk:4.15.8`
import com.baidu.aip.ocr.AipOcr
import java.util.HashMap
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import scala.jdk.CollectionConverters._
import javax.imageio.ImageIO
import java.io.PrintWriter
import scala.collection.immutable.Map
import scala.collection.immutable.SortedMap
import scala.collection.mutable.ListBuffer

@main(doc = "The pdf file brief info| 文件一瞥")
def info(f: String): Unit = {
  println(s"opening ${f} ")
  val doc: PDDocument = Loader.loadPDF(new File(f))
  brief(doc)
  // ocrScannedPdf(doc,f)
  // addOutLine(doc, "otlined.pdf", extractOutline() )

}

@main(doc = "Add bookmarks for every n page to f pdf file |  给pdf文件自动加书签")
def outline(
    @arg(doc = "File name with path | 文件名") f: String,
    @arg(doc = "Each n page, 1 for all pages | 每几页自动加书签") n: Int
) {
  println(s"opening ${f} ")
  val doc: PDDocument = Loader.loadPDF(new File(f))
  // brief(doc)
  val ocrs = ocrScannedPdf(doc, "ocr", n)
  val bms = SortedMap[Int, String](extractOutlineFromPages(ocrs).toSeq: _*)
  addOutLine(doc, f + "qnpdf-added.pdf", bms)

}

def addOutLine(document: PDDocument, f: String, ol: Map[Int, String]) {
  import org.apache.pdfbox.pdmodel.PDPage;
  import org.apache.pdfbox.pdmodel.PageMode;
  import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
  import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
  import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
  val bookmark_length_limit = 40
  val outline = new PDDocumentOutline();
  document.getDocumentCatalog().setDocumentOutline(outline);
  val pagesOutline = new PDOutlineItem();
  pagesOutline.setTitle("OCR-Generted");
  outline.addLast(pagesOutline);
  val pageNum = 0;
  val pages = document.getPages()
  for ((pg -> bm) <- ol) {
    val page: PDPage = pages.get(pg);
    val dest = new PDPageFitWidthDestination();
    dest.setPage(page);
    val bookmark = new PDOutlineItem();
    bookmark.setDestination(dest);
    bookmark.setTitle(
      bm.substring(0, Math.min(bm.length(), bookmark_length_limit)) + "--" + pg
    );
    pagesOutline.addLast(bookmark);
  }
  pagesOutline.openNode();
  outline.openNode();
  document.save(f);
}

def extractOutlineFromPages(ocrs: Map[Int, String]): Map[Int, String] = {
  for ((n -> content) <- ocrs) yield {
    val strs = traverse(ujson.read(content))
    val ccc = strs.mkString("")
    (n -> ccc)
  }
}

def ocrScannedPdf(doc: PDDocument, f: String, n: Int): Map[Int, String] = {
  val pgn = doc.getNumberOfPages() - n
  if (pgn < n) {
    println(s"n=${n} may too large | 书签添加页数设置过大")
    return Map.empty
  }
  (n to pgn by n).map { n =>
    {
      val pngFN = pdf2PNG(f, doc, n)
      val content = baiduOCR(pngFN)
      new PrintWriter(pngFN + ".json") { write(content); close }
      (n -> content)
    }
  }.toMap
}

// def ocrScannedPdf(doc: PDDocument, f: String): Map[String, Int] = {
//   ocrScannedPdf(doc, f, 1)
// }

def brief(doc: PDDocument) {
  printMetadata(doc)
  val outline = doc.getDocumentCatalog().getDocumentOutline()
  if (outline != null) {
    printBookmark(doc, outline, "")
  } else {
    println("This document does not contain any bookmarks")
  }
}

def isChapterTitle(x: String): Boolean = {
  x.matches("[\\u4E00-\\u9FA5a-zA-Z0-9.]+") && x.length > 3
}

def isPageNumber(x: String): Boolean = {
  x.matches("[0-9]+") && 1 <= x.length && 3 >= x.length
}

def traverse(v: ujson.Value): Iterable[String] = v match {
  case a: ujson.Arr => a.arr.flatMap(traverse)
  case o: ujson.Obj => o.obj.values.flatMap(traverse)
  case s: ujson.Str => Seq(s.str)
  case _            => Nil
}

def addBookmark(content: String, targetPage: Int) = {}

def baiduOCR(path: String): String = {
  val APP_ID = "24263801";
  val API_KEY = "s7d8Yf8nqZirnyOmWNK74iS6";
  val SECRET_KEY = "O6gVpHVldFLVEaAY1eVT1mhGSsOu7FKS";
  val client = new AipOcr(APP_ID, API_KEY, SECRET_KEY);
  val options = new HashMap[String, String]();
  options.put("recognize_granularity", "big");
  val res = client.general(path, options);
  System.out.println(res.toString(2));
  res.toString(2)
}

def pdf2PNG(f: String, document: PDDocument, page: Int): String = {
  val pdfRenderer = new PDFRenderer(document)
  val bufferedImage = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB)
  val fn = f + "-pg-" + page + ".png"
  val tempFile = new File(fn)
  ImageIO.write(bufferedImage, "png", tempFile)
  fn
}

def printBookmark(
    document: PDDocument,
    bookmark: PDOutlineNode,
    indentation: String
) {
  var current: PDOutlineItem = bookmark.getFirstChild()
  while (current != null) {
    if (current.getDestination().isInstanceOf[PDPageDestination]) {
      val pd: PDPageDestination =
        current.getDestination().asInstanceOf[PDPageDestination]
      println(
        indentation + "Destination page: " + (pd.retrievePageNumber() + 1)
      )
    } else if (current.getDestination().isInstanceOf[PDNamedDestination]) {
      val pd = document
        .getDocumentCatalog()
        .findNamedDestinationPage(
          current.getDestination().asInstanceOf[PDNamedDestination]
        )
      if (pd != null) {
        println(
          indentation + "Destination page: " + (pd.retrievePageNumber() + 1)
        )
      }
    } else if (current.getDestination() != null) {
      println(
        indentation + "Destination class: " + current
          .getDestination()
          .getClass()
          .getSimpleName()
      )
    }

    if (current.getAction().isInstanceOf[PDActionGoTo]) {
      val gta = current.getAction().asInstanceOf[PDActionGoTo]
      if (gta.getDestination().isInstanceOf[PDPageDestination]) {
        val pd = gta.getDestination().asInstanceOf[PDPageDestination]
        println(
          indentation + "Destination page: " + (pd.retrievePageNumber() + 1)
        )
      } else if (gta.getDestination().isInstanceOf[PDNamedDestination]) {
        val pd = document
          .getDocumentCatalog()
          .findNamedDestinationPage(
            gta.getDestination().asInstanceOf[PDNamedDestination]
          )
        if (pd != null) {
          println(
            indentation + "Destination page: " + (pd.retrievePageNumber() + 1)
          )
        }
      } else {
        println(
          indentation + "Destination class: " + gta
            .getDestination()
            .getClass()
            .getSimpleName()
        )
      }
    } else if (current.getAction() != null) {
      println(
        indentation + "Action class: " + current
          .getAction()
          .getClass()
          .getSimpleName()
      )
    }
    println(indentation + current.getTitle())
    printBookmark(document, current, indentation + "    ")
    current = current.getNextSibling()
  }
}

def printMetadata(document: PDDocument) = {
  val info = document.getDocumentInformation()
  val cat = document.getDocumentCatalog()
  val metadata = cat.getMetadata()
  println("Page Count=" + document.getNumberOfPages())
  println("Title=" + info.getTitle())
  println("Author=" + info.getAuthor())
  println("Subject=" + info.getSubject())
  println("Keywords=" + info.getKeywords())
  println("Creator=" + info.getCreator())
  println("Producer=" + info.getProducer())
  println("Creation Date=" + formatDate(info.getCreationDate()))
  println("Modification Date=" + formatDate(info.getModificationDate()))
  println("Trapped=" + info.getTrapped())
  if (metadata != null) {
    val string = new String(metadata.toByteArray(), StandardCharsets.ISO_8859_1)
    println("Metadata=" + string)
  }
}

def formatDate(date: Calendar): String = {
  if (date != null) {
    val formatter = new SimpleDateFormat()
    return formatter.format(date.getTime())
  }

  ""
}
