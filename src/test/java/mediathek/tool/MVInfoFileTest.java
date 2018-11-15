package mediathek.tool;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

import mSearch.daten.DatenFilm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MVInfoFileTest {


  private static final String DESCRIPTION_TEXT = "Weit hinten, hinter den Wortbergen, fern der Länder Vokalien und Konsonantien leben die Blindtexte. Abgeschieden wohnen sie in Buchstabhausen an der Küste des Semantik, eines großen Sprachozeans. Ein kleines Bächlein namens Duden fließt durch ihren Ort und versorgt sie mit den nötigen Regelialien. Es ist ein paradiesmatisches Land, in dem einem gebratene Satzteile in den Mund fliegen.";


  @Mock
  DatenFilm datenFilm;

  @Test
  void getMaxLengthFromStringArrayWithNullArray() {

    assertThat(MVInfoFile.getMaxLengthFromStringArray(null), is(equalTo(0)));
  }

  @Test
  void getMaxLengthFromStringArrayWithEmptyArray() {

    assertThat(MVInfoFile.getMaxLengthFromStringArray(new String[]{}), is(equalTo(0)));
  }

  @Test
  void getMaxLengthFromStringArrayWithOneItemLengthOf8() {

    assertThat(MVInfoFile.getMaxLengthFromStringArray(new String[]{"abcdefgh"}), is(equalTo(8)));

  }

  @Test
  void getMaxLengthFromStringArrayWithTwoItemsEachSameLengthOf4() {

    assertThat(MVInfoFile.getMaxLengthFromStringArray(new String[]{"abcd", "efgh"}), is(equalTo(4)));

  }

  @Test
  void getMaxLengthFromStringArrayWithSomeDifferentItems() {

    assertThat(MVInfoFile.getMaxLengthFromStringArray(new String[]{"Nr", "Filmnr", "Untertitel"}), is(equalTo(10)));

  }

  @Test
  void splittDescriptionTextIntoOneLine() {

    assertThat(MVInfoFile.splittStringIntoMaxFixedLengthLines("The Big Brown Fox Jumps over the Lazy Dog", 50), is(equalTo("The Big Brown Fox Jumps over the Lazy Dog")));

  }

  @Test
  void splittDescriptionTextIntoMore() {
    String result = "Weit hinten, hinter den Wortbergen, fern der Länder Vokalien" + System.lineSeparator()
                  + "und Konsonantien leben die Blindtexte. Abgeschieden wohnen sie" + System.lineSeparator()
                  + "in Buchstabhausen an der Küste des Semantik, eines großen" + System.lineSeparator()
                  + "Sprachozeans. Ein kleines Bächlein namens Duden fließt durch" + System.lineSeparator()
                  + "ihren Ort und versorgt sie mit den nötigen Regelialien. Es ist" + System.lineSeparator()
                  + "ein paradiesmatisches Land, in dem einem gebratene Satzteile" + System.lineSeparator()
                  + "in den Mund fliegen.";

    assertThat(MVInfoFile.splittStringIntoMaxFixedLengthLines(DESCRIPTION_TEXT, 62), is(equalTo(result)));
  }

  @Test
  void appendFormatedTableStringToEmptyStringBuilder() {
    StringBuilder sb = new StringBuilder();

    sb = MVInfoFile.appendFormatedTableLine(sb, "%-12s %s", "Größe [MB]", "194");

    assertThat(sb.toString(), is(equalTo("Größe [MB]:  194" + System.lineSeparator())));

  }

  @Test
  void formatFilmAsStringTestWithValidFilm() {

    Mockito.lenient().when(datenFilm.getSender()).thenReturn("BRX");
    Mockito.lenient().when(datenFilm.getThema()).thenReturn("Olafs Tag");
    Mockito.lenient().when(datenFilm.getTitle()).thenReturn("Olafs Tag mit Hans Wurst");
    Mockito.lenient().when(datenFilm.getSendeDatum()).thenReturn("25.01.2018");
    Mockito.lenient().when(datenFilm.getSendeZeit()).thenReturn("23:30:00");
    Mockito.lenient().when(datenFilm.getDauer()).thenReturn("00:59:00");
    Mockito.lenient().when(datenFilm.getSize()).thenReturn("889");
    Mockito.lenient().when(datenFilm.getWebsiteLink()).thenReturn("https://sha512.badssl.com/");
    Mockito.lenient().when(datenFilm.getUrl()).thenReturn("https://badssl.com/");
    Mockito.lenient().when(datenFilm.getDescription()).thenReturn(DESCRIPTION_TEXT);

    String result =
      "Sender:      BRX" + System.lineSeparator() +
      "Thema:       Olafs Tag" + System.lineSeparator() +
      "" + System.lineSeparator() +
      "Titel:       Olafs Tag mit Hans Wurst" + System.lineSeparator() +
      "" + System.lineSeparator() +
      "Datum:       25.01.2018" + System.lineSeparator() +
      "Zeit:        23:30:00" + System.lineSeparator() +
      "Dauer:       00:59:00" + System.lineSeparator() +
      "Größe [MB]:  889" + System.lineSeparator() +
      "" + System.lineSeparator() +
      "Website" + System.lineSeparator() +
      "https://sha512.badssl.com/" + System.lineSeparator() +
      "" + System.lineSeparator() +
      "URL" + System.lineSeparator() +
      "https://badssl.com/" + System.lineSeparator() +
      "" + System.lineSeparator() +
      "Weit hinten, hinter den Wortbergen, fern der Länder Vokalien" + System.lineSeparator() +
      "und Konsonantien leben die Blindtexte. Abgeschieden wohnen sie" + System.lineSeparator() +
      "in Buchstabhausen an der Küste des Semantik, eines großen" + System.lineSeparator() +
      "Sprachozeans. Ein kleines Bächlein namens Duden fließt durch" + System.lineSeparator() +
      "ihren Ort und versorgt sie mit den nötigen Regelialien. Es ist" + System.lineSeparator() +
      "ein paradiesmatisches Land, in dem einem gebratene Satzteile" + System.lineSeparator() +
      "in den Mund fliegen." + System.lineSeparator() + System.lineSeparator();

    assertThat(MVInfoFile.formatFilmAsString(datenFilm, DatenFilm.COLUMN_NAMES[DatenFilm.FILM_GROESSE].length() + 2), is(equalTo(result)));
  }

}