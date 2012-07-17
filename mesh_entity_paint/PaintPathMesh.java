package hyperden.mesh_entity_paint;

import hyperden.util.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;

import android.graphics.Rect;

public class PaintPathMesh implements Serializable {
    private static final long serialVersionUID = 1L;

    private class SubmeshNameDataMap extends TreeMap<String, PaintPathSubmesh> {
        private static final long serialVersionUID = 1L;
    }

    private SubmeshNameDataMap submeshNameDataMap_;
    public Rect aabb_;

    public class AnimTrackItem {
        public String submeshName_;
        public String animKeyName_;
    }

    public class AnimTrackItemList extends LinkedList<AnimTrackItem> {
        private static final long serialVersionUID = 1L;

        public AnimTrackItemList() {
        }

        public void addNewItem(String submeshName, String animKeyName) {
            AnimTrackItem newItem = new AnimTrackItem();
            newItem.submeshName_ = submeshName;
            newItem.animKeyName_ = animKeyName;
            add(newItem);
        }
    }

    public class AnimTrack {
        public static final float MIN_ANIM_TRACK_ITEM_TIME_DIFF = 0.1f;

        public class TimeBound {
            float startTime_;
            float endTime_;

            public TimeBound() {
                startTime_ = -1.f;
                endTime_ = -1.f;
            }

            public boolean isValid() {
                return (startTime_ >= 0) && (endTime_ >= 0);
            }
        };

        public AnimTrack() {
            timeItemsMap_ = new TimeItemsMap();
        }

        public class TimeItemsMap extends TreeMap<Float, AnimTrackItemList> {
            private static final long serialVersionUID = 1L;
        }

        private TimeItemsMap timeItemsMap_;

        public boolean hasAnimTrackItemList(float time) {
            return timeItemsMap_.containsKey(time);
        }

        public AnimTrackItemList addEmptyAnimTrackItemList(float time) {
            AnimTrackItemList newAnimTrackItems = new AnimTrackItemList();
            timeItemsMap_.put(time, newAnimTrackItems);
            return newAnimTrackItems;
        }

        public AnimTrackItemList getAnimTrackItemList(float time) {
            return timeItemsMap_.get(time);
        }

        public int getNumItems() {
            return timeItemsMap_.size();
        }

        public float getTimeLengh() {
            return timeItemsMap_.lastKey() - timeItemsMap_.firstKey();
        }

        public TimeBound getTimeBound(float time) {
            TimeBound timeBound = new TimeBound();
            Set<Float> keySet = timeItemsMap_.keySet();
            Iterator<Float> keyIter = keySet.iterator();
            float prevTime = -1;
            while (keyIter.hasNext()) {
                float keyTime = keyIter.next();
                if (Math.abs(keyTime - time) < MIN_ANIM_TRACK_ITEM_TIME_DIFF) {
                    timeBound.startTime_ = keyTime;
                    timeBound.endTime_ = keyTime;
                    return timeBound;
                }
                if ((prevTime >= 0) && (prevTime < time) &&
                        (time <= keyTime - MIN_ANIM_TRACK_ITEM_TIME_DIFF)) {
                    timeBound.startTime_ = prevTime;
                    timeBound.endTime_ = keyTime;
                    return timeBound;
                }
                prevTime = keyTime;
            }
            return timeBound;
        }
    }

    private class AnimTrackMap extends TreeMap<String, AnimTrack> {
        private static final long serialVersionUID = 1L;
    }

    private AnimTrackMap animTracks_;

    public enum EffectType {
        ENTITY_COLOR_MUL, // param1 = String "A R G B" (A, R, G, B = float)
        SUBENTITY_COLOR_MUL, // param1 = String "A R G B", 
                             // param2 = space-separated submesh names
        MIRROR_HORZ, // percent nonzero
        TORN_APART, // percent 1 ~ 100
        GROUND_UNIT_UPSIDE_DOWN, // percent 1 ~ 100
        SHOW_HIDDEN, // param1 = submesh name, percent nonzero
    };

    public static String getSubstringFromParamString(String param, int index) {
        int spaceStartPos = 0;
        for (int i = 0; i < index; i++) {
            spaceStartPos = param.indexOf(' ', spaceStartPos + 1);
        }
        if (spaceStartPos == param.lastIndexOf(' ')) {
            return param.substring(spaceStartPos);
        } else {
            int spaceEndPos = param.indexOf(' ', spaceStartPos + 1);
            return param.substring(spaceStartPos, spaceEndPos);
        }
    }

    public static int getIntFromParamString(String param, int index) {
        return Integer.parseInt(getSubstringFromParamString(param, index));
    }

    public static float getFloatFromParamString(String param, int index) {
        return Float.parseFloat(getSubstringFromParamString(param, index));
    }

    public class Effect {
        public EffectType type_;
        public String param1_;
        public String param2_;
    }

    public class EffectMap extends TreeMap<String, Effect> {
        private static final long serialVersionUID = 1L;
    }

    public EffectMap effectMap_;

    public PaintPathMesh() {
        submeshNameDataMap_ = new SubmeshNameDataMap();
        aabb_ = new Rect();
        animTracks_ = new AnimTrackMap();
        effectMap_ = new EffectMap();
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.writeObject(submeshNameDataMap_);
    }

    private void readObject(ObjectInputStream ois) throws
            OptionalDataException, ClassNotFoundException, IOException {
        submeshNameDataMap_ = (SubmeshNameDataMap) ois.readObject();

        aabb_ = new Rect();
        computeAabb();

        animTracks_ = new AnimTrackMap();
        effectMap_ = new EffectMap();
    }

    public int getNumSubmeshes() {
        return submeshNameDataMap_.size();
    }

    public String getFirstSubmeshName() {
        return submeshNameDataMap_.firstKey();
    }

    public String getLastSubmeshName() {
        return submeshNameDataMap_.lastKey();
    }

    public void addEmptySubmesh(String name, int color) {
        PaintPathSubmesh newSubmesh = new PaintPathSubmesh();
        newSubmesh.color_ = color;
        submeshNameDataMap_.put(name, newSubmesh);
    }

    public void addSubmesh(String name, PaintPathSubmesh ppsm) {
        submeshNameDataMap_.put(name, ppsm);
    }

    public boolean removeSubmesh(String name) {
        if (submeshNameDataMap_.containsKey(name)) {
            submeshNameDataMap_.remove(name);
            return true;
        }
        return false;
    }

    public PaintPathSubmesh getSubmesh(String name) {
        return submeshNameDataMap_.get(name);
    }

    public String getNextSubmeshName(String name) {
        Set<String> submeshNameSet = submeshNameDataMap_.keySet();
        Iterator<String> submeshNameIter = submeshNameSet.iterator();
        while (submeshNameIter.hasNext()) {
            String submeshName = submeshNameIter.next();
            if (name == submeshName) {
                if (submeshNameIter.hasNext()) {
                    return submeshNameIter.next();
                } else {
                    return "";
                }
            }
        }
        return "";
    }

    public String getPreviousSubmeshName(String name) {
        if (name.length() == 0) {
            return getLastSubmeshName();
        }
        Set<String> submeshNameSet = submeshNameDataMap_.keySet();
        Iterator<String> submeshNameIter = submeshNameSet.iterator();
        String previousName = null;
        while (submeshNameIter.hasNext()) {
            String submeshName = submeshNameIter.next();
            if (name == submeshName) {
                if (previousName == null) {
                    return "";
                } else {
                    return previousName;
                }
            }
            previousName = submeshName;
        }
        return "";
    }

    public Set<String> getAllSubmeshNames() {
        return submeshNameDataMap_.keySet();
    }

    public boolean hasAnyElementAtPos(int posDeciX, int posDeciY, int radiusDeci) {
        Collection<PaintPathSubmesh> submeshes = submeshNameDataMap_.values();
        Iterator<PaintPathSubmesh> submeshIter = submeshes.iterator();
        while (submeshIter.hasNext()) {
            PaintPathSubmesh submesh = submeshIter.next();
            if (submesh.hasAnyElementAtPos(posDeciX, posDeciY, radiusDeci)) {
                return true;
            }
        }
        return false;
    }

    public void computeAabb() {
        Collection<PaintPathSubmesh> submeshes = submeshNameDataMap_.values();
        Iterator<PaintPathSubmesh> submeshIter = submeshes.iterator();
        aabb_.setEmpty();
        while (submeshIter.hasNext()) {
            PaintPathSubmesh submesh = submeshIter.next();
            if (aabb_.isEmpty()) {
                aabb_.set(submesh.aabb_);
            } else {
                aabb_.union(submesh.aabb_);
            }
        }
    }

    public boolean hasSubmesh(String name) {
        Set<String> submeshNameSet = submeshNameDataMap_.keySet();
        return submeshNameSet.contains(name);
    }

    @Override
    protected PaintPathMesh clone() {
        PaintPathMesh clonedMesh = new PaintPathMesh();
        Set<String> submeshNameSet = submeshNameDataMap_.keySet();
        Iterator<String> submeshNameIter = submeshNameSet.iterator();
        while (submeshNameIter.hasNext()) {
            String submeshName = submeshNameIter.next();
            PaintPathSubmesh submesh = getSubmesh(submeshName);
            PaintPathSubmesh clonedSubmesh = submesh.clone();
            clonedMesh.addSubmesh(submeshName, clonedSubmesh);
        }
            
        // no need to clone anim track or effect - should be added to mesh_skeleton.xml manually

        return clonedMesh;
    }

    public void logAllSubmeshNames() {
        Set<String> submeshNameSet = submeshNameDataMap_.keySet();
        Iterator<String> submeshNameIter = submeshNameSet.iterator();
        String logStr = "All submeshes: ";
        while (submeshNameIter.hasNext()) {
            String submeshName = submeshNameIter.next();
            logStr += submeshName + " ";
        }
        Logger.logInfo(logStr);
    }

    public String getFirstAnimTrackName() {
        return animTracks_.firstKey();
    }

    public String getNextAnimTrackName(String name) {
        if (name.length() == 0) {
            return getFirstAnimTrackName();
        }
        Set<String> animTrackNameSet = animTracks_.keySet();
        Iterator<String> animTrackNameIter = animTrackNameSet.iterator();
        while (animTrackNameIter.hasNext()) {
            String animTrackName = animTrackNameIter.next();
            if (animTrackName == name) {
                if (animTrackNameIter.hasNext()) {
                    return animTrackNameIter.next();
                } else {
                    return "";
                }
            }
        }
        return "";
    }

    public String getLastAnimTrackName() {
        return animTracks_.lastKey();
    }

    public String getPrevAnimTrackName(String name) {
        if (name.length() == 0) {
            return getLastAnimTrackName();
        }
        Set<String> animTrackNameSet = animTracks_.keySet();
        Iterator<String> animTrackNameIter = animTrackNameSet.iterator();
        String previousName = null;
        while (animTrackNameIter.hasNext()) {
            String animTrackName = animTrackNameIter.next();
            if (animTrackName == name) {
                if (previousName == null) {
                    return "";
                } else {
                    return previousName;
                }
            }
            previousName = animTrackName;
        }
        return "";
    }

    public AnimTrack getAnimTrack(String name) {
        return animTracks_.get(name);
    }

    public int getNumAnimTracks() {
        return animTracks_.size();
    }

    public boolean hasAnimTrack(String name) {
        return animTracks_.containsKey(name);
    }

    public void addEmptyAnimTrack(String name) {
        AnimTrack newAnimTrack = new AnimTrack();
        animTracks_.put(name, newAnimTrack);
    }

    public boolean hasEffect(String name) {
        return effectMap_.containsKey(name);
    }

    public Effect addEmptyEffect(String name) {
        Effect newEffect = new Effect();
        effectMap_.put(name, newEffect);
        return newEffect;
    }

    public Set<String> getAllEffectNames() {
        return effectMap_.keySet();
    }

    public int getNumEffects() {
        return effectMap_.size();
    }

    public Effect getEffect(String name) {
        return effectMap_.get(name);
    }

    public String getFirstEffectName() {
        return effectMap_.firstKey();
    }

    public String getNextEffect(String name) {
        if (name.length() == 0) {
            return getFirstEffectName();
        }
        Set<String> effectNameSet = effectMap_.keySet();
        Iterator<String> effectNameIter = effectNameSet.iterator();
        while (effectNameIter.hasNext()) {
            String effectName = effectNameIter.next();
            if (effectName == name) {
                if (effectNameIter.hasNext()) {
                    return effectNameIter.next();
                } else {
                    return "";
                }
            }
        }
        return "";
    }
}
