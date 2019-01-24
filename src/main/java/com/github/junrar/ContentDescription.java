package com.github.junrar;

public class ContentDescription {
    public String path;
    public long size;

    public ContentDescription(String path, long size) {
        this.path = path;
        this.size = size;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        result = prime * result + (int) (size ^ (size >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ContentDescription other = (ContentDescription) obj;
        if (path == null) {
            if (other.path != null) {
                return false;
            }
        } else if (!path.equals(other.path)) {
            return false;
        }
        if (size != other.size) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return path + ": " + size;
    }


}
