package thredds.inventory;

import thredds.featurecollection.FeatureCollectionConfig;
import thredds.filesystem.MFileOS7;
import thredds.inventory.partition.DirectoryCollection;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.units.TimeDuration;
import ucar.unidata.util.StringUtil2;

import java.io.IOException;
import java.util.*;

/**
 * A Collection of MFiles
 *
 * @author caron
 * @since 11/20/13
 */
public abstract class CollectionAbstract implements MCollection {
  static private org.slf4j.Logger defaultLog = org.slf4j.LoggerFactory.getLogger("featureCollectionScan");

  static public final String CATALOG = "catalog:";
  static public final String DIR = "directory:";
  static public final String FILE = "file:";
  static public final String LIST = "list:";

    // called from Aggregation, Fmrc, FeatureDatasetFactoryManager
  static public MCollection open(String collectionName, String collectionSpec, String olderThan, Formatter errlog) throws IOException {
    if (collectionSpec.startsWith(CATALOG))
      return new CollectionManagerCatalog(collectionName, collectionSpec.substring(CATALOG.length()), olderThan, errlog);
    else if (collectionSpec.startsWith(DIR))
      return new DirectoryCollection(collectionName, collectionSpec.substring(DIR.length()), null);
    else if (collectionSpec.startsWith(FILE))
      return new CollectionSingleFile(MFileOS7.getExistingFile(collectionSpec.substring(FILE.length())), null);
    else if (collectionSpec.startsWith(LIST))
      return new CollectionList(collectionName, collectionSpec.substring(LIST.length()), null);
    else
      return MFileCollectionManager.open(collectionName, collectionSpec, olderThan, errlog);
  }

  static public String cleanName(String name) {
    if (name == null) return null;
    return StringUtil2.replace(name.trim(), ' ', "_");  // LOOK must be ok in URL - probably not sufficient here
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  protected String collectionName;
  protected String root;
  protected final org.slf4j.Logger logger;

  protected FeatureCollectionConfig.ProtoChoice protoChoice = FeatureCollectionConfig.ProtoChoice.Penultimate;  // default

  protected Map<String, Object> auxInfo; // lazy init
  protected boolean isPartition; // true if partition, else GribCollection

  protected DateExtractor dateExtractor;
  protected CalendarDate startCollection;

  protected CollectionAbstract( String collectionName, org.slf4j.Logger logger) {
    this.collectionName = cleanName(collectionName);
    this.logger = logger != null ? logger : defaultLog;
  }

  @Override
  public boolean isPartition() {
    return isPartition;
  }

  public void setPartition(boolean partition) {
    isPartition = partition;
  }

  @Override
  public String getCollectionName() {
    return collectionName;
  }

  @Override
  public String getRoot() {
    return root;
  }

  public void setRoot(String root) {
    this.root = root;
  }

  @Override
  public MFile getLatestFile() throws IOException {
    MFile result = null;
    for (MFile f: getFilesSorted()) // only have an Iterable
      result = f;
    return result;
  }

  @Override
  public List<String> getFilenames() throws IOException {
    List<String> result = new ArrayList<>();
    for (MFile f: getFilesSorted())
      result.add(f.getPath());
    return result;
  }

  @Override
  public CalendarDate extractDate(MFile mfile) {
    return (dateExtractor == null) ? null : dateExtractor.getCalendarDate(mfile);
  }

  @Override
  public boolean hasDateExtractor() {
    return (dateExtractor != null);
  }

  public void setDateExtractor(DateExtractor dateExtractor) {
    this.dateExtractor = dateExtractor;
  }

  @Override
  public CalendarDate getStartCollection() {
    return startCollection;
  }

  ////////////////////////////////////////////////////
  // ability to pass arbitrary information in. kind of a kludge

  @Override
  public Object getAuxInfo(String key) {
    return auxInfo == null ? null : auxInfo.get(key);
  }

  @Override
  public void putAuxInfo(String key, Object value) {
    if (auxInfo == null) auxInfo = new HashMap<>();
    auxInfo.put(key, value);
  }

    ////////////////////////////////////////////////////
  // proto dataset choosing

  @Override
  public int getProtoIndex(int n) {
    if (n < 2) return 0;

    int protoIdx = 0;
    switch (protoChoice) {
      case First:
        protoIdx = 0;
        break;
      case Random:
        Random r = new Random(System.currentTimeMillis());
        protoIdx = r.nextInt(n - 1);
        break;
      case Run:
      case Penultimate:
        protoIdx = Math.max(n - 2, 0);
        break;
      case Latest:
        protoIdx = Math.max(n - 1, 0);
        break;
    }
    return protoIdx;
  }

  public class DateSorter implements Comparator<MFile> {
    public int compare(MFile m1, MFile m2) {
      CalendarDate cd1 = extractRunDateWithError(m1);
      CalendarDate cd2 = extractRunDateWithError(m2);
      if ((cd1 == null) || (cd2 == null)) {
         cd1 = extractRunDateWithError(m1);  //debug
         cd2 = extractRunDateWithError(m2);
      }
      return cd1.compareTo(cd2);
    }
  }

  private CalendarDate extractRunDateWithError(MFile mfile) {
    CalendarDate result = extractDate(mfile);
    if (result == null)
      logger.error("Failed to extract date from file {} with Extractor {}", mfile.getPath(), dateExtractor);
    return result;
  }
}

