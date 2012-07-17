package hyperden.mesh_entity_paint;

import hyperden.heli_one.app.air_combat.R;
import hyperden.util.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

public class PaintPathMeshes implements Serializable {
    private static final long serialVersionUID = 1L;
    public static boolean serializationSuccess_;

    private class MeshNameDataMap extends TreeMap<String, PaintPathMesh> {
        private static final long serialVersionUID = 1L;

    }

    private MeshNameDataMap meshNameDataMap_;

    public PaintPathMeshes() {
        meshNameDataMap_ = new MeshNameDataMap();
    }

    private void writeObject(ObjectOutputStream oos) {
        serializationSuccess_ = false;
        try {
            oos.writeObject(meshNameDataMap_);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readObject(ObjectInputStream ois) {
        serializationSuccess_ = false;
        try {
            meshNameDataMap_ = (MeshNameDataMap) ois.readObject();
            serializationSuccess_ = true;
        } catch (OptionalDataException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean serialize(FileOutputStream fos) {
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(fos);
            oos.writeObject(this);
            serializationSuccess_ = true;
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    static public PaintPathMeshes createFromResource(Resources resources,
            int logLevel) {
        // step 1. deserialize meshes raw data

        PaintPathMeshes ppm = null;
        InputStream rris = resources.openRawResource(R.raw.meshes_anim_data);
        try {
            ObjectInputStream ois;
            ois = new ObjectInputStream(rris);
            ppm = (PaintPathMeshes) ois.readObject();
            ois.close();
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        // step 2. read mesh skeleton XML (submesh hierarchy, anim track, etc)
        Logger.logInfo("start xml parsing from resource submesh_hierarchy");
        XmlResourceParser xmlParser = resources.getXml(R.xml.mesh_skeleton);
        PaintPathMesh mesh = null;
        PaintPathSubmesh submeshChild = null;
        PaintPathSubmesh submeshParent = null;
        String submeshChildName = null;
        String submeshParentName = null;
        String animTrackName = null;
        float animTrackItemTime = -1.f;
        int eventType;
        try {
            eventType = xmlParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_DOCUMENT) {
                    if (logLevel >= 1) {
                        Logger.logInfo("Start document");
                    }
                } else if (eventType == XmlPullParser.START_TAG) {
                    String tagName = xmlParser.getName();
                    if (logLevel >= 2) {
                        Logger.logInfo("Start tag " + tagName + " num attr " +
                                xmlParser.getAttributeCount());
                    }
                    if (tagName.equals("mesh")) {
                        if (mesh != null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName + " but mesh is not null");
                            return null;
                        }
                        submeshChild = null;
                        submeshParent = null;
                        for (int i = 0; i < xmlParser.getAttributeCount(); i++) {
                            if (logLevel >= 1) {
                                Logger.logInfo("  mesh attr " + i + ": "
                                        + xmlParser.getAttributeName(i) +
                                        " => " + xmlParser.getAttributeValue(i));
                            }
                            if (xmlParser.getAttributeName(i).equals("name")) {
                                String meshName = xmlParser
                                        .getAttributeValue(i);
                                if (!ppm.hasMesh(meshName)) {
                                    Log.e("H",
                                            "mesh_skeleton xml parse ERROR: mesh with name " +
                                                    meshName + " not found");
                                    ppm.logAllMeshNames();
                                    return null;
                                }
                                mesh = ppm.getMesh(meshName);
                            }
                        }
                        if (mesh == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: attr name not found in mesh tag");
                            return null;
                        }
                    } else if (tagName.equals("submesh")) {
                        if (mesh == null) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but mesh is null");
                            return null;
                        }
                        if (animTrackName != null) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but animTrackName is not null");
                            return null;
                        }
                        for (int i = 0; i < xmlParser.getAttributeCount(); i++) {
                            if (logLevel >= 2) {
                                Logger.logInfo("  submesh attr " + i + ": "
                                        + xmlParser.getAttributeName(i) +
                                        " => " + xmlParser.getAttributeValue(i));
                            }
                            if (xmlParser.getAttributeName(i).equals("name")) {
                                String submeshName = xmlParser
                                        .getAttributeValue(i);
                                if (!mesh.hasSubmesh(submeshName)) {
                                    Log.e("H",
                                            "mesh_skeleton xml parse ERROR: submesh with name " +
                                                    submeshName + " not found");
                                    mesh.logAllSubmeshNames();
                                    return null;
                                }
                                if (submeshChild == null) {
                                    submeshChild = mesh.getSubmesh(submeshName);
                                    submeshChildName = submeshName;
                                } else {
                                    submeshParent = submeshChild;
                                    submeshParentName = submeshChildName;
                                    submeshChild = mesh.getSubmesh(submeshName);
                                    submeshChildName = submeshName;
                                    submeshParent.childSubmeshNameList_
                                            .add(submeshName);
                                    submeshChild.parentSubmeshName_ = submeshParentName;
                                    if (logLevel >= 1) {
                                        Logger.logInfo("    submesh hierarchy: "
                                                + submeshParentName + " --> "
                                                + submeshName);
                                    }
                                }
                            } else if (xmlParser.getAttributeName(i).equals("hidden")) {
                                String hidden = xmlParser.getAttributeValue(i);
                                if (hidden.equals("1")) {
                                    submeshChild.hidden_ = true;
                                } else {
                                    Log.e("H", "mesh_skeleton xml parse ERROR: attr value for hidden is not 1");
                                    return null;
                                }
                            } else if (xmlParser.getAttributeName(i).equals("excludeFromLOD")) {
                                String excludeFromLOD = xmlParser.getAttributeValue(i);
                                if (excludeFromLOD.equals("1")) {
                                    submeshChild.excludeFromLOD_ = true;
                                } else {
                                    Log.e("H", "mesh_skeleton xml parse ERROR: attr value for excludeFromLOD is not 1");
                                    return null;
                                }
                            } else if (xmlParser.getAttributeName(i).equals("excludeFromBlowUp")) {
                                String hidden = xmlParser.getAttributeValue(i);
                                if (hidden.equals("1")) {
                                    submeshChild.excludeFromTornApart_ = true;
                                } else {
                                    Log.e("H",
                                            "mesh_skeleton xml parse ERROR: attr value for excludeFromBlowUp is not 1");
                                    return null;
                                }
                            }
                        }
                        if (submeshChild == null) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: attr name not found in submesh tag");
                            return null;
                        }
                    } else if (tagName.equals("animTrack")) {
                        if (mesh == null) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: start tag " + 
                            		tagName + " but mesh is null");
                            return null;
                        }
                        if (animTrackName != null) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: start tag " + tagName + 
                            		" but animTrackName is not null");
                            return null;
                        }
                        animTrackName = null;
                        for (int i = 0; i < xmlParser.getAttributeCount(); i++) {
                            if (logLevel >= 2) {
                                Logger.logInfo("  animTrack attr " + i + ": "
                                        + xmlParser.getAttributeName(i) +
                                        " => " + xmlParser.getAttributeValue(i));
                            }
                            if (xmlParser.getAttributeName(i).equals("name")) {
                                animTrackName = xmlParser.getAttributeValue(i);
                            }
                        }
                        if (animTrackName == null) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: animTrackName not found mesh");
                            return null;
                        }
                        if (mesh.hasAnimTrack(animTrackName)) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: animTrackName " +
                                            animTrackName + " already exists in mesh");
                            return null;
                        }
                        mesh.addEmptyAnimTrack(animTrackName);
                    } else if (tagName.equals("animTrackItem")) {
                        if (mesh == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but mesh is null");
                            return null;
                        }
                        if (animTrackName == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but animTrackName is null");
                            return null;
                        }
                        if (animTrackItemTime >= 0) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but animTrackItemTime is "
                                            + animTrackItemTime);
                            return null;
                        }
                        float prevAnimTrackItemTime = animTrackItemTime;
                        for (int i = 0; i < xmlParser.getAttributeCount(); i++) {
                            if (logLevel >= 2) {
                                Logger.logInfo("  animTrackItem attr " + i + ": "
                                        + xmlParser.getAttributeName(i) +
                                        " => " + xmlParser.getAttributeValue(i));
                            }
                            if (xmlParser.getAttributeName(i).equals("time")) {
                                animTrackItemTime = Float.parseFloat(xmlParser
                                        .getAttributeValue(i));
                                if (animTrackItemTime < 0) {
                                    Log.e("H",
                                            "mesh_skeleton xml parse ERROR: animTrackItemTime is "
                                                    + animTrackItemTime
                                                    + " but cannot be negative");
                                    return null;
                                }
                            }
                        }
                        if ((animTrackItemTime - prevAnimTrackItemTime) < 
                        		PaintPathMesh.AnimTrack.MIN_ANIM_TRACK_ITEM_TIME_DIFF * 2) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: attr time in animTrackItemTime is only "
                                            + (animTrackItemTime - prevAnimTrackItemTime)
                                            +
                                            " greater than the previous item time pos");
                            return null;
                        }
                        PaintPathMesh.AnimTrack animTrack = mesh
                                .getAnimTrack(animTrackName);
                        if ((animTrack.getNumItems() == 0)
                                && (animTrackItemTime != 0.f)) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: attr time in animTrackItemTime is "
                                            +
                                            animTrackItemTime
                                            + " but the first timePos must be 0");
                            return null;
                        }
                        if (animTrack.hasAnimTrackItemList(animTrackItemTime)) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: animTrackItemTime "
                                            + animTrackItemTime +
                                            "already exists in "
                                            + animTrackName);
                            return null;
                        }
                        animTrack.addEmptyAnimTrackItemList(animTrackItemTime);
                    } else if (tagName.equals("animTrackSubmesh")) {
                        if (animTrackName == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but animTrackName is null");
                            return null;
                        }
                        PaintPathMesh.AnimTrack animTrack = mesh
                                .getAnimTrack(animTrackName);
                        if (animTrackItemTime < 0) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but animTrackItemTime is "
                                            + animTrackItemTime);
                            return null;
                        }
                        if (!animTrack.hasAnimTrackItemList(animTrackItemTime)) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " animTrack does not have time "
                                            + animTrackItemTime);
                            return null;
                        }
                        PaintPathMesh.AnimTrackItemList animTrackItems = animTrack
                                .getAnimTrackItemList(
                                animTrackItemTime);

                        String animSubmeshName = null;
                        String animKeyName = null;
                        for (int i = 0; i < xmlParser.getAttributeCount(); i++) {
                            if (logLevel >= 2) {
                                Logger.logInfo("  animTrackSubmesh attr " + i
                                        + ": " + xmlParser.getAttributeName(i) +
                                        " => " + xmlParser.getAttributeValue(i));
                            }
                            if (xmlParser.getAttributeName(i).equals(
                                    "submeshName")) {
                                animSubmeshName = xmlParser
                                        .getAttributeValue(i);
                            } else if (xmlParser.getAttributeName(i).equals(
                                    "animKeyName")) {
                                animKeyName = xmlParser.getAttributeValue(i);
                            }
                        }
                        if (animSubmeshName == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: animSubmeshName not found");
                            return null;
                        }
                        if (animKeyName == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: animKeyName not found");
                            return null;
                        }
                        if (!mesh.hasSubmesh(animSubmeshName)) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: in anim key "
                                            + animKeyName +
                                            ", submesh " + animSubmeshName
                                            + " not found");
                            return null;
                        }
                        if (logLevel >= 1) {
                            PaintPathSubmesh submesh = mesh
                                    .getSubmesh(animSubmeshName);
                            if (logLevel >= 2) {
                                Logger.logInfo("  check with " + animTrackItems.size()
                                                + " animTrackItem(s) and "
                                                + mesh.getNumSubmeshes()
                                                + " submeshe(s)?");
                            }
                            Iterator<PaintPathMesh.AnimTrackItem> animTrackItemIter = animTrackItems
                                    .iterator();
                            while (animTrackItemIter.hasNext()) {
                                PaintPathMesh.AnimTrackItem existingAnimTrackItem = animTrackItemIter
                                        .next();
                                while (submesh.parentSubmeshName_.length() > 0) {
                                    if (logLevel >= 2) {
                                        Logger.logInfo("    checking if animTrackItem with submesh "
                                                        +
                                                        existingAnimTrackItem.submeshName_
                                                        +
                                                        " is NOT the new animTrackItem with submesh "
                                                        +
                                                        submesh.parentSubmeshName_);
                                    }
                                    if (existingAnimTrackItem.submeshName_ == submesh.parentSubmeshName_) {
                                        Log.e("H",
                                                "mesh_skeleton xml parse ERROR: new animTrackitem with submesh "
                                                        + animSubmeshName
                                                        + " is parent of existing track item with "
                                                        + submesh.parentSubmeshName_);
                                        return null;
                                    }
                                    submesh = mesh
                                            .getSubmesh(submesh.parentSubmeshName_);
                                }
                            }
                        }
                        animTrackItems.addNewItem(animSubmeshName, animKeyName);
                    } else if (tagName.equals("effect")) {
                        if (mesh == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but mesh is null");
                            return null;
                        }
                        if (animTrackName != null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but animTrackName is not null");
                            return null;
                        }
                        String effectName = null;
                        String effectType = null;
                        String effectParam1 = null;
                        String effectParam2 = null;
                        for (int i = 0; i < xmlParser.getAttributeCount(); i++) {
                            if (logLevel >= 2) {
                                Logger.logInfo("  effect attr " + i + ": "
                                        + xmlParser.getAttributeName(i) +
                                        " => " + xmlParser.getAttributeValue(i));
                            }
                            if (xmlParser.getAttributeName(i).equals("name")) {
                                effectName = xmlParser.getAttributeValue(i);
                            } else if (xmlParser.getAttributeName(i).equals(
                                    "type")) {
                                effectType = xmlParser.getAttributeValue(i);
                            } else if (xmlParser.getAttributeName(i).equals(
                                    "param1")) {
                                effectParam1 = xmlParser.getAttributeValue(i);
                            } else if (xmlParser.getAttributeName(i).equals(
                                    "param2")) {
                                effectParam2 = xmlParser.getAttributeValue(i);
                            }
                        }
                        if (effectName == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but effectName is null");
                            return null;
                        }
                        if (effectType == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName
                                            + " but effectType is null");
                            return null;
                        }
                        if (mesh.hasEffect(effectName)) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName +
                                            " byt effect with name "
                                            + effectName + " already exists");
                            return null;
                        }
                        PaintPathMesh.Effect newEffect = mesh.addEmptyEffect(effectName);
                        try {
                            newEffect.type_ = PaintPathMesh.EffectType.valueOf(effectType);
                        } catch (IllegalArgumentException e) {
                            Log.e("H", "mesh_skeleton xml parse ERROR: start tag "
                                            + tagName + ": " + e);
                            return null;
                        }
                        newEffect.param1_ = effectParam1;
                        newEffect.param2_ = effectParam2;
                    } else if (tagName.equals("meshes")) {
                        // ok
                    } else {
                        Log.e("H",
                                "mesh_skeleton xml parse ERROR: unexpected start tag "
                                        + tagName);
                        return null;
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String tagName = xmlParser.getName();
                    if (logLevel >= 2) {
                        Logger.logInfo("End tag " + tagName);
                    }
                    if (tagName.equals("mesh")) {
                        if (mesh == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: end tag "
                                            + tagName
                                            + " but mesh is null");
                            return null;
                        }
                        mesh = null;
                    } else if (tagName.equals("submesh")) {
                        if (submeshChild == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: end tag "
                                            + tagName
                                            + " but submesh child is null");
                            return null;
                        }
                        if (submeshParent != null) {
                            submeshChild = submeshParent;
                            submeshChildName = submeshParentName;
                            if (submeshParent.parentSubmeshName_.length() > 0) {
                                submeshParent = mesh
                                        .getSubmesh(submeshParentName);
                                submeshParentName = submeshParent.parentSubmeshName_;
                            } else {
                                submeshParent = null;
                                submeshParentName = null;
                            }
                        } else {
                            submeshChild = null;
                            submeshChildName = null;
                        }
                    } else if (tagName.equals("animTrack")) {
                        if (animTrackName == null) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: end tag "
                                            + tagName
                                            + " but animTrackName is null");
                            return null;
                        }
                        if (logLevel >= 1) {
                            PaintPathMesh.AnimTrack animTrack = mesh
                                    .getAnimTrack(animTrackName);
                            Logger.logInfo("    got anim track " + animTrackName
                                    + " with " +
                                    animTrack.getNumItems() + " item(s), " +
                                    animTrack.getTimeLengh() + "sec");
                        }
                        PaintPathMesh.AnimTrack animTrack = mesh
                                .getAnimTrack(animTrackName);
                        if (animTrack.getNumItems() < 2) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: end tag "
                                            + tagName
                                            + " for " + animTrackName
                                            + " but animTrack.getNumItems is "
                                            + animTrack.getNumItems());
                            return null;
                        }
                        animTrackName = null;
                    } else if (tagName.equals("animTrackItem")) {
                        if (animTrackItemTime < 0) {
                            Log.e("H",
                                    "mesh_skeleton xml parse ERROR: end tag "
                                            + tagName
                                            + " but animTrackNameItemTime is "
                                            + animTrackItemTime);
                            return null;
                        }
                        animTrackItemTime = -1.f;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                }
                eventType = xmlParser.next();
                if (eventType == XmlPullParser.END_DOCUMENT) {
                    if (logLevel >= 1) {
                        Logger.logInfo("End document");
                    }
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        if (logLevel >= 1) {
            boolean verified = ppm.logAllMeshSubmeshAnimKeys();
            if (!verified) {
                return null;
            }
        }

        return ppm;
    }

    public int getNumMeshes() {
        return meshNameDataMap_.size();
    }

    public String getFirstMeshName() {
        return meshNameDataMap_.firstKey();
    }

    public String getLastMeshName() {
        return meshNameDataMap_.lastKey();
    }

    public void addEmptyMesh(String name) {
        PaintPathMesh newMesh = new PaintPathMesh();
        meshNameDataMap_.put(name, newMesh);
    }

    public void addMesh(String name, PaintPathMesh ppm) {
        meshNameDataMap_.put(name, ppm);
    }

    public boolean removeMesh(String name) {
        if (meshNameDataMap_.containsKey(name)) {
            meshNameDataMap_.remove(name);
            return true;
        }
        return false;
    }

    public boolean hasMesh(String name) {
        Set<String> keySet = meshNameDataMap_.keySet();
        return (keySet.contains(name));
    }

    public PaintPathMesh getMesh(String name) {
        return meshNameDataMap_.get(name);
    }

    public String getNextMeshName(String name) {
        Set<String> meshNameSet = meshNameDataMap_.keySet();
        Iterator<String> meshNameIter = meshNameSet.iterator();
        while (meshNameIter.hasNext()) {
            String meshName = meshNameIter.next();
            if (name == meshName) {
                if (meshNameIter.hasNext()) {
                    return meshNameIter.next();
                } else {
                    return "";
                }
            }
        }
        return "";
    }

    public String getPreviousMeshName(String name) {
        if (name.length() == 0) {
            return getLastMeshName();
        }
        Set<String> meshNameSet = meshNameDataMap_.keySet();
        Iterator<String> meshNameIter = meshNameSet.iterator();
        String previousName = null;
        while (meshNameIter.hasNext()) {
            String meshName = meshNameIter.next();
            if (name == meshName) {
                if (previousName == null) {
                    return "";
                } else {
                    return previousName;
                }
            }
            previousName = meshName;
        }
        return "";
    }

    public void logAllMeshNames() {
        Set<String> meshNameSet = meshNameDataMap_.keySet();
        Iterator<String> meshNameIter = meshNameSet.iterator();
        String logStr = "All meshes: ";
        while (meshNameIter.hasNext()) {
            String meshName = meshNameIter.next();
            logStr += "  " + meshName + "\n";
        }
        Logger.logInfo(logStr);
    }

    public boolean logAllMeshSubmeshAnimKeys() {
        Logger.logInfo("====== All meshes/submeshes/animKeys ======");
        Set<String> meshNameSet = meshNameDataMap_.keySet();
        Iterator<String> meshNameIter = meshNameSet.iterator();
        while (meshNameIter.hasNext()) {
            String meshName = meshNameIter.next();
            Logger.logInfo("  mesh: " + meshName);
            PaintPathMesh mesh = meshNameDataMap_.get(meshName);
            Set<String> submeshNameSet = mesh.getAllSubmeshNames();
            Iterator<String> submeshNameIter = submeshNameSet.iterator();
            while (submeshNameIter.hasNext()) {
                String submeshName = submeshNameIter.next();
                if (submeshName.contains("@")) {
                    Log.e("H", "ERROR: submeshName should not have @: "
                            + submeshName);
                    return false;
                }
                Logger.logInfo("    submesh: " + submeshName);
                PaintPathSubmesh submesh = mesh.getSubmesh(submeshName);
                Set<String> animKeySet = submesh.getAllAnimKeys();
                Iterator<String> animKeyNameIter = animKeySet.iterator();
                while (animKeyNameIter.hasNext()) {
                    String animKeyName = animKeyNameIter.next();
                    if (animKeyName.contains("@")) {
                        Log.e("H", "ERROR: animKey should not have @: "
                                + animKeyName);
                        return false;
                    }
                    Logger.logInfo("    ..animKey: " + animKeyName);
                }
            }

            Set<String> effectNames = mesh.getAllEffectNames();
            Iterator<String> effectNameIter = effectNames.iterator();
            while (effectNameIter.hasNext()) {
                String effectName = effectNameIter.next();
                Logger.logInfo("    effect: " + effectName + "..");
                PaintPathMesh.Effect effect = mesh.getEffect(effectName);
                if ((effect.type_ == PaintPathMesh.EffectType.ENTITY_COLOR_MUL) ||
                        (effect.type_ == PaintPathMesh.EffectType.SUBENTITY_COLOR_MUL)) {
                    PaintPathMesh.getFloatFromParamString(effect.param1_, 0);
                    PaintPathMesh.getFloatFromParamString(effect.param1_, 1);
                    PaintPathMesh.getFloatFromParamString(effect.param1_, 2);
                    PaintPathMesh.getFloatFromParamString(effect.param1_, 3);
                }
                if (effect.type_ == PaintPathMesh.EffectType.SUBENTITY_COLOR_MUL) {
        			String[] paramSplit2 = effect.param2_.split(" ");
        			for (int i = 0; i < paramSplit2.length; i++) {
	                    if (!mesh.hasSubmesh(paramSplit2[i])) {
	                        Log.e("H", "for effect " + effectName + " submesh "
	                                + effect.param2_
	                                + " not found");
	                        return false;
	                    }
        			}
                }
            }
        }
        return true;
    }
}
