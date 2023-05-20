import java.io.IOException;
import java.util.*;

public class ConcurrentArrayList<E> implements List<E> {
    // these are marked volatile as they are shared between threads
    // initially def_empty can hold 128 elements
    private static final Object[] def_empty = new Object[1 << 7];

    // we need to experiment with this value to find the optimal value
    private final double GROWTH_FACTOR = 1.5;

    private volatile Object[] array;
    private volatile int size;

    ConcurrentArrayList() {
        this.array = def_empty;
        size = 0;
    }

    private ConcurrentArrayList<E> fromArray(Object[] array) {
        ConcurrentArrayList<E> list = new ConcurrentArrayList<>();

        list.array = array;
        list.size = array.length;

        return list;
    }

    private Object[] expand() {

        int newCapacity = (int) (array.length * GROWTH_FACTOR);
        Object[] newArray = new Object[newCapacity];
        System.arraycopy(array, 0, newArray, 0, array.length);
        return newArray;
    }

    public boolean add(Object o) {
        synchronized (this) {
            int currentSize = size;
            if (currentSize == array.length) {
                Object[] newArray = expand();
                newArray[currentSize] = o;
                array = newArray;
            } else {
                array[currentSize] = o;
            }
            size = currentSize + 1;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public E get(int index) {
        synchronized (this){
            rangeCheck(index);
            return (E) array[index];
        }
    }


    /**
     * Replaces the element at the specified position in this list with the specified element (optional operation).
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     */

    @Override
    public E set(int index, Object element) {
        rangeCheck(index);

        E old = get(index);

        synchronized (this) {
            array[index] = element;
        }

        return old;
    }

    @Override
    public void add(int index, Object element) {
        rangeCheck(index);

        synchronized (this) {
            array[index] = element;
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public E remove(int index) {
        rangeCheck(index);

        synchronized (this) {
            Object removed = array[index];

            if (size - 1 - index >= 0) System.arraycopy(array, index + 1, array, index, size - 1 - index);

            size--;

            return (E) removed;
        }
    }

    @Override
    public int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (array[i].equals(o)) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        int lastIndex = -1;

        for (int i = 0; i < size; i++) {
            if (array[i].equals(o)) {
                lastIndex = i;
            }
        }

        return lastIndex;
    }

    @Override
    public ListIterator<E> listIterator() {
        return new parallel_iterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new parallel_iterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException();
        }

        rangeCheck(fromIndex);
        rangeCheck(toIndex);

        Object[] newArray = new Object[toIndex - fromIndex];

        System.arraycopy(array, fromIndex, newArray, 0, toIndex - fromIndex);

        return fromArray(newArray);
    }

    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean contains(Object o) {
        for (Object element : array) {
            if (element.equals(o)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Iterator<E> iterator() {
        // TODO: in the future we can implement an iterator based on the parallel_iterator
        return new parallel_iterator();
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOf(array, size);
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }


    @Override
    public boolean remove(Object o) {
        int index = indexOf(o);

        if (index == -1) {
            return false;
        }

        remove(index);

        return true;
    }

    @Override
    public boolean addAll(Collection <? extends E> c) {
        return addAll(size, c);
    }

    @Override
    public boolean addAll(int index, Collection <? extends E> c) {
        rangeCheck(index);

        int cSize = c.size();

        if (cSize == 0) {
            return false;
        }

        synchronized (this) {
            Object[] newArray = new Object[size + cSize];

            System.arraycopy(array, 0, newArray, 0, index);
            System.arraycopy(c.toArray(), 0, newArray, index, cSize);
            System.arraycopy(array, index, newArray, index + cSize, size - index);

            array = newArray;
            size += cSize;
        }

        return true;
    }

    @Override
    public void clear() {
        synchronized (this) {
            array = def_empty;
            size = 0;
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean remo = false;

        for (Iterator<E> it = iterator(); it.hasNext();) {
            if (!c.contains(it.next())) {
                it.remove();
                remo = true;
            }
        }

        return remo;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        // TODO: we can look for a more efficient way to do this

        boolean changed = false;

        synchronized(this) {

            for (Object element : c) {
                changed |= remove(element);
            }

        }

        return changed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean containsAll(Collection c) {
        HashSet<Object> set = new HashSet<Object>(c);
        boolean contains = true;

        int count = 0;

        for (Object element : array) {
            count += (set.contains(element)) ? 1 : 0;
        }

        return count == c.size();
    }

    private void rangeCheck(int index) {
        // TODO: if you want more explicit errors or exceptions
        if (index >= size) {
            throw new IndexOutOfBoundsException();
        }

        if (index < 0) {
            throw new IndexOutOfBoundsException();
        }
    }


    /* List iterator  */
    // if you feel like it, you can also try to implement a thread safe Iterator<E>
    // see in iterator() and listIterator() methods
    private class parallel_iterator implements ListIterator<E> {
        volatile int cursor = 0;
        volatile int lastRet = -1;

        parallel_iterator(){}

        parallel_iterator(int index) {
            cursor = index;
            lastRet = -1;
        }

        @Override
        public boolean hasNext() {
            return cursor != size;
        }

        @Override
        @SuppressWarnings("unchecked")
        public E next() {
            int i = cursor;
            synchronized (this) {
                if (i >= size) {
                    throw new IndexOutOfBoundsException();
                }

                Object[] elementData = ConcurrentArrayList.this.array;

                cursor = i + 1;

                return (E) elementData[lastRet = i];
            }
        }

        @Override
        public boolean hasPrevious() {
            return cursor != 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public E previous() {
            int i = cursor - 1;

            if (i < 0) {
                throw new IndexOutOfBoundsException();
            }

            Object[] elementData = ConcurrentArrayList.this.array;


            return (E) elementData[lastRet = i];
        }

        @Override
        public int nextIndex() {
            return cursor;
        }

        @Override
        public int previousIndex() {
            return cursor - 1;
        }

        @Override
        public void remove() {
            synchronized (this) {
                if (lastRet < 0) {
                    throw new IllegalStateException();
                }

                ConcurrentArrayList.this.remove(lastRet);

                cursor = lastRet;
                lastRet = -1;
            }
        }

        @Override
        public void set(E e) {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }

            ConcurrentArrayList.this.set(lastRet, e);

        }

        @Override
        public void add(E e) {
            int i = cursor;

            ConcurrentArrayList.this.add(i, e);

            cursor = i + 1;
            lastRet = -1;
        }
    }

}
