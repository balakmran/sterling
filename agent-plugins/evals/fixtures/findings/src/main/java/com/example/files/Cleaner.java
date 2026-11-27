package com.example.files;

import java.io.File;

/** Removes temporary files. */
public final class Cleaner {
  /** Deletes the file and says nothing about whether that worked. */
  public void remove(File file) {
    file.delete();
  }

  /** Counts the files that are gone. */
  public int countMissing(File... files) {
    int missing = 0;
    for (File file : files) {
      if (!file.exists()) {
        missing++;
      }
    }
    return missing;
  }

  /** Names this cleaner. */
  public String toString() {
    return "Cleaner";
  }

  /** Counts the files that are there. */
  public int countPresent(File... files) {
    return files.length - countMissing(files);
  }
}
