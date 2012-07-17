package hyperden.mesh_entity_paint;

import hyperden.util.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeMap;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;

public class PaintPathSubmesh implements Serializable {
    private static final long serialVersionUID = 1L;
    public int color_;
    public Paint.Style paintStyle_;
    public int strokeWidth_;
    public Path originalElemPath_;
    public Rect aabb_;
    public boolean hidden_;
    public boolean excludeFromTornApart_;
    public boolean excludeFromLOD_;

    private class ElementList extends LinkedList<PaintPathElement> {
        private static final long serialVersionUID = 1L;
    }

    private ElementList elementList_;

    public class ChildSubmeshNameList extends LinkedList<String> {
        private static final long serialVersionUID = 1L;
    }

    public ChildSubmeshNameList childSubmeshNameList_;
    public String parentSubmeshName_;

    public class AnimKey implements Serializable {
        public Matrix transformMatrix_;
        public Matrix parentTransformMatrix_;

        private static final long serialVersionUID = 1L;

        public float translationX_;
        public float translationY_;
        public float rotationDeg_;
        public float rotationPivotX_;
        public float rotationPivotY_;
        public float scaleX_;
        public float scaleY_;
        public float scalePivotX_;
        public float scalePivotY_;

        public AnimKey() {
            transformMatrix_ = new Matrix();
            parentTransformMatrix_ = new Matrix();
            reset();
        }

        private void writeObject(ObjectOutputStream oos) throws IOException {
            oos.writeFloat(translationX_);
            oos.writeFloat(translationY_);
            oos.writeFloat(rotationDeg_);
            oos.writeFloat(rotationPivotX_);
            oos.writeFloat(rotationPivotY_);
            oos.writeFloat(scaleX_);
            oos.writeFloat(scaleY_);
            oos.writeFloat(scalePivotX_);
            oos.writeFloat(scalePivotY_);
        }

        private void readObject(ObjectInputStream ois) throws
                OptionalDataException, ClassNotFoundException, IOException {
            translationX_ = ois.readFloat();
            translationY_ = ois.readFloat();
            rotationDeg_ = ois.readFloat();
            rotationPivotX_ = ois.readFloat();
            rotationPivotY_ = ois.readFloat();
            scaleX_ = ois.readFloat();
            scaleY_ = ois.readFloat();
            scalePivotX_ = ois.readFloat();
            scalePivotY_ = ois.readFloat();

            transformMatrix_ = new Matrix();
        }
        
        public AnimKey clone() {
        	AnimKey clonedAnimKey = new AnimKey();
        	clonedAnimKey.translationX_ = translationX_;
        	clonedAnimKey.translationY_ = translationY_;
        	clonedAnimKey.rotationDeg_ = rotationDeg_;
        	clonedAnimKey.rotationPivotX_ = rotationPivotX_;
        	clonedAnimKey.rotationPivotY_ = rotationPivotY_;
        	clonedAnimKey.scaleX_ = scaleX_;
        	clonedAnimKey.scaleY_ = scaleY_;
        	clonedAnimKey.scalePivotX_ = scalePivotX_;
        	clonedAnimKey.scalePivotY_ = scalePivotY_;
        	return clonedAnimKey;
        }

        public void setFromMatrix() {
            // to do (need?)
        }

        public void resetTranslation() {
            translationX_ = 0.f;
            translationY_ = 0.f;
        }

        public void resetRotation() {
            rotationDeg_ = 0.f;
        }

        public void resetScale() {
            scaleX_ = 1.f;
            scaleY_ = 1.f;
        }

        public void reset() {
            resetTranslation();
            resetRotation();
            resetScale();
        }

        public Matrix getMatrix(int percent, boolean applyEffectMirrowHorz) {
            transformMatrix_.reset();
            float percentFloat = (float) percent / 100.f;
            if ((translationX_ != 1.f) || (translationY_ != 1.f)) {
                transformMatrix_.setTranslate(
                        percentFloat * translationX_,
                        percentFloat * translationY_);
            }
            float scalePivotX = scalePivotX_;
            if (applyEffectMirrowHorz) {
                scalePivotX = scalePivotX * -1;
            }
            if ((scaleX_ != 1.f) || (scaleY_ != 1.f)) {
                transformMatrix_.postScale(
                        1.f + percentFloat * (scaleX_ - 1.f),
                        1.f + percentFloat * (scaleY_ - 1.f),
                        scalePivotX, scalePivotY_);
            }
            float rotationPivotX = rotationPivotX_;
            if (applyEffectMirrowHorz) {
                rotationPivotX = rotationPivotX * -1;
            }
            if (rotationDeg_ != 0.f) {
                transformMatrix_.postRotate(percentFloat * rotationDeg_,
                        rotationPivotX, rotationPivotY_);
            }
            return transformMatrix_;
        }
    }

    public class AnimKeyMap extends TreeMap<String, AnimKey> {
        private static final long serialVersionUID = 1L;
    };

    protected AnimKeyMap animKeys_;

    public PaintPathSubmesh() {
        elementList_ = new ElementList();
        paintStyle_ = Paint.Style.FILL;
        color_ = Color.WHITE;
        childSubmeshNameList_ = new ChildSubmeshNameList();
        parentSubmeshName_ = new String();
        animKeys_ = new AnimKeyMap();
        hidden_ = false;
        excludeFromTornApart_ = false;
        if (null == originalElemPath_) {
            originalElemPath_ = new Path();
            aabb_ = new Rect();
        }
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.writeInt(color_);
        oos.writeObject(paintStyle_);
        oos.writeFloat(strokeWidth_);
        oos.writeObject(elementList_);
        oos.writeObject(animKeys_);
    }

    private void readObject(ObjectInputStream ois) throws
            OptionalDataException, ClassNotFoundException, IOException {
        color_ = ois.readInt();
        paintStyle_ = (Paint.Style) ois.readObject();
        strokeWidth_ = ois.readInt();
        if (strokeWidth_ > 10) {
            strokeWidth_ = 0;
        }
        if (strokeWidth_ < 0) {
            strokeWidth_ = 0;
        }
        elementList_ = (ElementList) ois.readObject();
        if (null == originalElemPath_) {
            originalElemPath_ = new Path();
            aabb_ = new Rect();
        }
        computeOriginalPath();

        childSubmeshNameList_ = new ChildSubmeshNameList();
        parentSubmeshName_ = new String();
        animKeys_ = (AnimKeyMap) ois.readObject();
        hidden_ = false;
        excludeFromTornApart_ = false;
    }

    public int appendElement(PaintPathElement.PathType type, int posDeciX,
            int posDeciY) {
        PaintPathElement newElem = new PaintPathElement();
        newElem.pathType_ = type;
        newElem.posDeciX_ = posDeciX;
        newElem.posDeciY_ = posDeciY;
        return appendElement(newElem);
    }

    public int appendElement(PaintPathElement newElem) {
        elementList_.add(newElem);
        computeOriginalPath();
        return elementList_.size();
    }

    public boolean removeElement(int index) {
        if (this.getNumElements() > index) {
            elementList_.remove(index);
            computeOriginalPath();
            return true;
        } else {
            return false;
        }
    }

    public boolean insertDupElement(int index) {
        if ((this.getNumElements() > index) && (index > 0)) {
            PaintPathElement thisElem = this.getElement(index);
            PaintPathElement prevElem = this.getElement(index - 1);
            PaintPathElement newElem = thisElem.clone();
            newElem.posDeciX_ = (prevElem.posDeciX_ + thisElem.posDeciX_) / 2;
            newElem.posDeciY_ = (prevElem.posDeciY_ + thisElem.posDeciY_) / 2;
            elementList_.add(index, newElem);
            computeOriginalPath();
            return true;
        } else {
            return false;
        }
    }

    public void computeOriginalPath() {
        aabb_.setEmpty();
        originalElemPath_.reset();
        Rect elemAabb = new Rect();
        ListIterator<PaintPathElement> iter = elementList_.listIterator();
        while (iter.hasNext()) {
            PaintPathElement elem = iter.next();
            if (elem.pathType_ == PaintPathElement.PathType.MOVE_TO) {
                originalElemPath_.moveTo(
                        (float) elem.posDeciX_ / 10.f,
                        (float) elem.posDeciY_ / 10.f);
            } else if (elem.pathType_ == PaintPathElement.PathType.LINE_TO) {
                originalElemPath_.lineTo(
                        (float) elem.posDeciX_ / 10.f,
                        (float) elem.posDeciY_ / 10.f);
            }

            elemAabb.set(elem.posDeciX_ / 10 - 1, elem.posDeciY_ / 10 - 1,
                    elem.posDeciX_ / 10 + 1, elem.posDeciY_ / 10 + 1);
            if (aabb_.isEmpty()) {
                aabb_.set(elemAabb);
            } else {
                aabb_.union(elemAabb);
            }
        }
    }

    public int getNumElements() {
        return elementList_.size();
    }

    public PaintPathElement getElement(int index) {
        ListIterator<PaintPathElement> iter = elementList_.listIterator();
        for (int i = 0; i < index; i++) {
            iter.next();
        }
        return iter.next();
    }

    public boolean hasAnyElementAtPos(int posDeciX, int posDeciY, int radiusDeci) {
        ListIterator<PaintPathElement> iter = elementList_.listIterator();
        while (iter.hasNext()) {
        	try {
        		PaintPathElement elem = iter.next();
        		int distSum = Math.abs(posDeciX - elem.posDeciX_) +
        		Math.abs(posDeciY - elem.posDeciY_);
        		if (distSum < radiusDeci) {
        			return true;
        		}
        	} catch (ConcurrentModificationException e) {
        		return false;
        	}
        }
        return false;
    }

    public void setColor(int color) {
        color_ = color;
    }

    public void setPaintStyle(Paint.Style style) {
        paintStyle_ = style;
    }

    public void setStrokeWidth(int strokeWidth) {
        strokeWidth_ = strokeWidth;
    }

    public void moveAllElements(int diffDeciX, int diffDeciY) {
        ListIterator<PaintPathElement> iter = elementList_.listIterator();
        while (iter.hasNext()) {
            PaintPathElement elem = iter.next();
            elem.posDeciX_ += diffDeciX;
            elem.posDeciY_ += diffDeciY;
        }
        computeOriginalPath();
    }

    public void scaleAllElements(int diffDeciX, int diffDeciY) {
        float scaleX = 1.f + (float) diffDeciX / 100.f;
        float scaleY = 1.f + (float) diffDeciY / 100.f;
        ListIterator<PaintPathElement> iter = elementList_.listIterator();
        while (iter.hasNext()) {
            PaintPathElement elem = iter.next();
            elem.posDeciX_ = (int) ((float) elem.posDeciX_ * scaleX);
            elem.posDeciY_ = (int) ((float) elem.posDeciY_ * scaleY);
        }
        computeOriginalPath();
    }

    public boolean hasAnimTransform(String keyName) {
        Set<String> keySet = animKeys_.keySet();
        return (keySet.contains(keyName));
    }

    public AnimKey getAnimKey(String keyName) {
        return animKeys_.get(keyName);
    }

    public AnimKey addNewAnimKey(String keyName) {
        AnimKey animTransform = new AnimKey();
        animKeys_.put(keyName, animTransform);
        return animTransform;
    }

    public void addAnimKey(String keyName, AnimKey animTransform) {
        animKeys_.put(keyName, animTransform);
    }

    public int getNumAnimKeys() {
        return animKeys_.keySet().size();
    }

    public void removeAnim(String keyName) {
        animKeys_.remove(keyName);
    }

    public void replaceAnim(String keyName, AnimKey animTransform) {
        animKeys_.remove(keyName);
        animKeys_.put(keyName, animTransform);
    }

    public String getFirstAnimKey() {
        return animKeys_.firstKey();
    }

    public String getLastAnimKey() {
        return animKeys_.lastKey();
    }

    public Set<String> getAllAnimKeys() {
        return animKeys_.keySet();
    }

    public String getNextAnimKey(String name) {
        if (name.length() == 0) {
            return getFirstAnimKey();
        }
        Set<String> keySet = animKeys_.keySet();
        Iterator<String> keyIter = keySet.iterator();
        while (keyIter.hasNext()) {
            String keyName = keyIter.next();
            if (keyName == name) {
                if (keyIter.hasNext()) {
                    return keyIter.next();
                } else {
                    return "";
                }
            }
        }
        return "";
    }

    public String getPreviousAnimKey(String name) {
        if (name.length() == 0) {
            return getLastAnimKey();
        }
        Set<String> keySet = animKeys_.keySet();
        Iterator<String> keyIter = keySet.iterator();
        String previousName = null;
        while (keyIter.hasNext()) {
            String nameIter = keyIter.next();
            if (name == nameIter) {
                if (previousName == null) {
                    return "";
                } else {
                    return previousName;
                }
            }
            previousName = nameIter;
        }
        return "";
    }

    @Override
    protected PaintPathSubmesh clone() {
        PaintPathSubmesh clonedSubmesh = new PaintPathSubmesh();
        ListIterator<PaintPathElement> iter = elementList_.listIterator();
        while (iter.hasNext()) {
            PaintPathElement elem = iter.next();
            PaintPathElement clonedElem = elem.clone();
            clonedSubmesh.elementList_.add(clonedElem);
        }
        clonedSubmesh.paintStyle_ = paintStyle_;
        clonedSubmesh.strokeWidth_ = strokeWidth_;
        clonedSubmesh.color_ = color_;
        clonedSubmesh.computeOriginalPath();

        Set<String> animKeySet = animKeys_.keySet();
        Iterator<String> animKeyIter = animKeySet.iterator();
        while (animKeyIter.hasNext()) {
            String nameIter = animKeyIter.next();
            AnimKey animKey = animKeys_.get(nameIter);
            AnimKey clonedAnimKey = animKey.clone();
            clonedSubmesh.addAnimKey(nameIter, clonedAnimKey);
        }
        
        return clonedSubmesh;
    }

    protected void mirrorHorz() {
        ListIterator<PaintPathElement> iter = elementList_.listIterator();
        while (iter.hasNext()) {
            PaintPathElement elem = iter.next();
            elem.posDeciX_ = -1 * elem.posDeciX_;
        }
    }

    protected void logDescription(String description) {
        Logger.logInfo("log all elems " + description);
        ListIterator<PaintPathElement> iter = elementList_.listIterator();
        while (iter.hasNext()) {
            PaintPathElement elem = iter.next();
            Logger.logInfo("  " + elem.getDescription());
        }
    }
}
