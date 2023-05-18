import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ConcurrentArrayList<E> implements List<E> {
    // these are marked volatile as they are shared between threads
    // we avoid explicit locking by using copy on write
    private static final Object[] def_empty = {};

    private volatile Object[] array;
    private volatile int size;
    private volatile int maxSize = 50;

    ConcurrentArrayList() {
        this.array = new Object[maxSize];
        size = 0;
    }

    private ConcurrentArrayList<E> fromArray(Object[] array) {
        ConcurrentArrayList<E> list = new ConcurrentArrayList<>();

        list.array = array;
        list.size = array.length;

        return list;
    }

    public boolean add(Object o) {

        synchronized (this) {
            if(size == maxSize){
                maxSize *= 2;
                Object[] newArray = new Object[maxSize];
                System.arraycopy(array, 0, newArray, 0, array.length);
                newArray[size++] = o;
                array = newArray;
                return true;
            }

            array[size++] = o;
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    public E get(int index) {
        synchronized (this){
            if(index >= size) throw new IndexOutOfBoundsException("Array size is " + size + " but index " + index + " was requested");
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
            Object[] newArray = new Object[size + 1];

            System.arraycopy(array, 0, newArray, 0, index);
            newArray[index] = element;
            System.arraycopy(array, index, newArray, index + 1, size - index);

            array = newArray;
            size++;
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public E remove(int index) {
        rangeCheck(index);

        synchronized (this) {
            Object[] newArray = new Object[size - 1];
            Object removed = array[index];

            System.arraycopy(array, 0, newArray, 0, index);
            System.arraycopy(array, index + 1, newArray, index, size - index - 1);

            array = newArray;
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
        // TODO: if you want implement Iterator
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
        // this is poorly optimized

        boolean changed = false;

        synchronized(this) {

            for (Object element : c) {
                changed |= remove(element);
            }

        }

        return changed;
    }

    @Override
    public boolean containsAll(Collection c) {
        boolean contains = true;

        Object[] copy;
        synchronized (this) {
            copy = Arrays.copyOf(array, size);
        }

        for (Object element : c) {
            contains &= Arrays.asList(copy).contains(element);
        }

        return contains;
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