package cn.dancingsnow.neoecoae.platform;

/** Java 17 implementation of the range semantics used by Java 21 Math.clamp call sites. */
public final class JdkCompat {
    private JdkCompat() {}
    public static <T>T first(java.util.List<T> values){if(values.isEmpty())throw new java.util.NoSuchElementException();return values.get(0);}
    public static <T>T first(T[] values){if(values.length==0)throw new java.util.NoSuchElementException();return values[0];}
    public static <T>T first(java.util.Deque<T> values){return values.getFirst();}
    public static <T>T last(java.util.List<T> values){if(values.isEmpty())throw new java.util.NoSuchElementException();return values.get(values.size()-1);}
    public static <T>T last(java.util.Deque<T> values){return values.getLast();}
    public static <T>java.util.List<T> reversed(java.util.List<T> values){return new java.util.AbstractList<>(){
        @Override public T get(int index){java.util.Objects.checkIndex(index,size());return values.get(size()-1-index);}
        @Override public int size(){return values.size();}
        @Override public T set(int index,T value){java.util.Objects.checkIndex(index,size());return values.set(size()-1-index,value);}
        @Override public T remove(int index){java.util.Objects.checkIndex(index,size());return values.remove(size()-1-index);}
        @Override public void add(int index,T value){if(index<0||index>size())throw new IndexOutOfBoundsException(index);values.add(size()-index,value);}
    };}

    public static int clamp(long value, int min, int max) {
        if (min > max) throw new IllegalArgumentException("Inverted integer bounds");
        return (int) Math.min(max, Math.max(value, min));
    }

    public static long clamp(long value, long min, long max) {
        if (min > max) throw new IllegalArgumentException("Inverted long bounds");
        return Math.min(max, Math.max(value, min));
    }

    public static float clamp(float value, float min, float max) {
        if (Float.isNaN(min) || Float.isNaN(max) || Float.compare(min, max) > 0) {
            throw new IllegalArgumentException("Invalid float bounds");
        }
        return Math.min(max, Math.max(value, min));
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max) || Double.compare(min, max) > 0) {
            throw new IllegalArgumentException("Invalid double bounds");
        }
        return Math.min(max, Math.max(value, min));
    }
}
