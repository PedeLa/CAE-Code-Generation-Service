package i5.las2peer.services.codeGenerationService.traces.segments;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;

public class SynchronizeOrderedAppendableVariableSegment
    extends SynchronizeAppendableVariableSegment {


  public SynchronizeOrderedAppendableVariableSegment(String id, AppendableVariableSegment segment) {
    super(id, segment);
  }

  /**
   * Private method to get the list of segments in the same order as for the old segments
   * 
   * @return A list of segments in the same order of the old segments
   */

  private List<String> getReorderedChildrenList() {
    List<String> alreadyAdded = new ArrayList<String>();
    List<String> reordered = new ArrayList<String>();
    System.out.println(this.getId());
    for (String id : this.compositeSegment.getChildrenList()) {
      System.out.println(id);
      Segment segment = this.getChild(id);

      if (segment != null && !alreadyAdded.contains(id)) {
        reordered.add(id);
        alreadyAdded.add(id);
      }
    }

    for (String id : this.getChildrenList()) {
      if (!alreadyAdded.contains(id)) {
        reordered.add(id);
        alreadyAdded.add(id);
      }
    }
    return reordered;
  }

  @Override
  public JSONObject toJSONObject() {
    return this.toJSONObject(this.getReorderedChildrenList());
  }

  @Override
  public String toString() {
    return this.toString(this.getReorderedChildrenList());
  }

}
