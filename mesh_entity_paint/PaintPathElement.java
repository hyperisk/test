package hyperden.mesh_entity_paint;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.Serializable;

public class PaintPathElement implements Serializable {
    private static final long serialVersionUID = 1L;

    public static enum PathType {
        MOVE_TO,
        LINE_TO
    };

    public PathType pathType_;
    public int posDeciX_;
    public int posDeciY_;

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.writeInt(pathType_.ordinal());
        oos.writeInt(posDeciX_);
        oos.writeInt(posDeciY_);
    }

    private void readObject(ObjectInputStream ois) throws
            OptionalDataException, ClassNotFoundException, IOException {
        pathType_ = PathType.values()[ois.readInt()];
        posDeciX_ = ois.readInt();
        posDeciY_ = ois.readInt();
    }

    @Override
    protected PaintPathElement clone() {
        PaintPathElement newElem = new PaintPathElement();
        newElem.pathType_ = pathType_;
        newElem.posDeciX_ = posDeciX_;
        newElem.posDeciY_ = posDeciY_;
        return newElem;
    }

    protected String getDescription() {
        return "pos: " + (float) posDeciX_ / 10.f + ", " + (float) posDeciY_
                / 10.f;
    }
};
