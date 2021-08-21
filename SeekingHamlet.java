import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

class SeekingHamlet {

    private static Map<Word, List<Integer>> dictionary;
    final static int blockSize = 0x400 * 4;

    private static boolean isLetter(byte c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
    }

    static void preProcess(String fileName) {
        dictionary = new TreeMap<>();
        try (SeekableByteChannel channel = Files.newByteChannel(Paths.get(fileName))) {
            ByteBuffer buffer = ByteBuffer.allocate(blockSize);
            int start, end, blocks, carry;
            start = end = carry = 0;
            blocks = -1;
            boolean seek = true;
            while (channel.read(buffer) > 0) {
                blocks++;
                byte[] data = buffer.array();
                if(seek){
                    while(start < blockSize && !isLetter(data[start])){
                        start++;
                    }
                    end = start + 1;
                }
                while (end < blockSize) {
                    if (isLetter(data[end])) {
                        end++;
                        continue;
                    }
                    int wordSize = end - start;
                    byte[] word = new byte[wordSize];
                    buffer.position(start);
                    buffer.get(word, 0, wordSize);
                    Word w = Word.of(word);
                    dictionary.computeIfAbsent(w, (it) -> new ArrayList<>())
                              .add(blockSize * blocks - carry + start);
                    start = end + 1;
                    while(start < blockSize && !isLetter(data[start])){
                        start++;
                    }
                    end = start + 1;
                }
                // We're in the middle of a word
                if(start < blockSize){
                    buffer.position(start);
                    buffer.compact();
                    end = blockSize - start;
                    carry += end;
                    start = 0;
                    seek = false;
                } else { // We're in a split token, discard it
                    buffer.position(0);
                    start = 0;
                    end = 0;
                    seek = true;
                }
            }
        } catch (IOException ex) {
            System.err.printf("Couldn't process input file: %s", ex.getMessage());
        }
    }

    public static void main(String[] args) {
        final String fileName = "hamlet.txt";
        preProcess(fileName);
        int numberOfWords = dictionary.size();
        dictionary.forEach((word, locations) -> {
            System.out.printf("%s is present at locations: %s\n", word, locations);
        });
    }
}

class Word implements Comparable<Word> {
    private final byte[] data;

    private Word(byte[] data){
        this.data = data;
    }

    static Word of(byte[] data){
        byte[] clone = Arrays.copyOf(data, data.length);
        convertToLowerCase(clone);
        return new Word(clone);
    }

    private static void convertToLowerCase(byte[] array){
        // Since we control the Word creation we know that all bytes correspond to letters so skipping that check
        for(int i = 0; i < array.length; i++){
            if('a' <= array[i] && array[i] <= 'z')
                continue;
            array[i] -= 'A';
            array[i] += 'a';
        }
    }

    @Override
    public int compareTo(Word other){
        return Arrays.compareUnsigned(this.data, other.data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Word word = (Word) o;
        return Arrays.equals(data, word.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString(){
        return Charset.defaultCharset()
                      .decode(ByteBuffer.wrap(data)).toString();
    }
}