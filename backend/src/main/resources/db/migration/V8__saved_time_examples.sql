CREATE TABLE saved_time_example (
    min_seconds INTEGER NOT NULL,
    max_seconds INTEGER,
    topic TEXT NOT NULL,
    sentence TEXT NOT NULL,
    PRIMARY KEY (min_seconds, topic),
    CHECK (max_seconds IS NULL OR max_seconds > min_seconds)
);

CREATE TABLE saved_time_example_history (
    id INTEGER PRIMARY KEY,
    min_seconds INTEGER NOT NULL,
    topic TEXT NOT NULL,
    sentence_key TEXT NOT NULL
);

INSERT INTO saved_time_example (min_seconds, max_seconds, topic, sentence) VALUES
    (30, 60, 'kwiaty', 'Zdążysz podlać kwiatka i przeprosić go za ostatni tydzień.'),
    (30, 60, 'sznurowadla', 'Zdążysz zawiązać oba buty, nawet jeśli jeden sznur ma własne plany.'),
    (30, 60, 'mandarynka', 'Zdążysz obrać mandarynkę i zostawić komuś najlepszą cząstkę.'),
    (30, 60, 'woda', 'Zdążysz nalać sobie wody i wznieść toast za brak korków.'),
    (60, 120, 'tost', 'Zdążysz posmarować tost dżemem aż po samą skórkę.'),
    (60, 120, 'zdjecie', 'Zdążysz zrobić zdjęcie kubka, zanim wystygnie jego kariera w internecie.'),
    (60, 120, 'pocztówka', 'Zdążysz wypisać pocztówkę i zmieścić pozdrowienia dla całej rodziny.'),
    (60, 120, 'skarpetki', 'Zdążysz znaleźć parę skarpetek i urządzić im szczęśliwe spotkanie.'),
    (120, 240, 'herbata', 'Zdążysz zaparzyć miętę i wybrać kubek godny tej okazji.'),
    (120, 240, 'kanapka', 'Zdążysz zrobić kanapkę i równo rozdzielić ostatnie plasterki sera.'),
    (120, 240, 'papier', 'Zdążysz złożyć papierowy samolot i przeprowadzić pierwszy lot próbny.'),
    (120, 240, 'krzyzowka', 'Zdążysz rozwiązać kilka haseł w krzyżówce i zostawić najtrudniejsze na później.'),
    (240, 420, 'piosenka', 'Zdążysz wysłuchać całej piosenki i zaśpiewać refren, choć nikt o to nie prosił.'),
    (240, 420, 'polka', 'Zdążysz uporządkować półkę i znaleźć rzecz, której od tygodnia nie szukasz.'),
    (240, 420, 'jajecznica', 'Zdążysz usmażyć jajecznicę i uznać, że szczypiorek się liczy jako dekoracja.'),
    (240, 420, 'sudoku', 'Zdążysz wpisać kilka cyfr w sudoku i przez chwilę poczuć się jak detektyw.'),
    (420, 600, 'zupa', 'Zdążysz zjeść talerz zupy, nawet jeśli dmuchasz w każdą łyżkę.'),
    (420, 600, 'ksiazka', 'Zdążysz przeczytać kilka stron książki i wreszcie przesunąć zakładkę.'),
    (420, 600, 'plecak', 'Zdążysz spakować plecak i sprawdzić, czy klucze są w tej tajnej kieszeni.'),
    (420, 600, 'spacer', 'Zdążysz przejść się wokół bloku i sprawdzić, co słychać u osiedlowych kotów.'),
    (600, NULL, 'salatka', 'Zdążysz zrobić sałatkę i pokroić ogórka w prawie równe plasterki.'),
    (600, NULL, 'rozmowa', 'Zdążysz zadzwonić do znajomego i usłyszeć pierwszą część historii o jego remoncie.'),
    (600, NULL, 'pranie', 'Zdążysz poskładać pranie i odkryć, która skarpetka znów podróżuje solo.'),
    (600, NULL, 'rysunek', 'Zdążysz naszkicować widok z okna i dać drzewom trochę więcej liści.');
