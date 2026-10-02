SELECT [id_zakázky], [číslo_zakázky], [id_subjektu]
FROM (SELECT [IDHDOK] AS [id_zakázky], [CIS_DOK] AS [číslo_zakázky], [IDSUBJEKT] AS [id_subjektu], [VLSTAVHDOK] AS [stav_zakázky]
        FROM (
    SELECT * FROM QHDOK_ZAK WHERE TYP_DOK='POB'
) AS [zakázka__filter]) AS [t]
WHERE [id_zakázky] = :zakázka_id AND [stav_zakázky] = 1
