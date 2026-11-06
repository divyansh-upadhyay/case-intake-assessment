package dev.caseintake;
import java.nio.file.*;
/** Offline model exchange: returns a previously recorded response. */
public final class ReplayModelClient implements ModelClient {
 private final Path fixture;
 public ReplayModelClient(Path fixture){this.fixture=fixture;}
 @Override public String extract(String prompt) throws Exception {return Files.readString(fixture);}
}
