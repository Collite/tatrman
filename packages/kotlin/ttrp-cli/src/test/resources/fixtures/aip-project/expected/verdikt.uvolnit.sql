SELECT *
FROM (SELECT [id_zakázky], [číslo_zakázky], [id_subjektu]
        FROM (SELECT [IDHDOK] AS [id_zakázky], [CIS_DOK] AS [číslo_zakázky], [IDSUBJEKT] AS [id_subjektu], [VLSTAVHDOK] AS [stav_zakázky]
                FROM (
    SELECT * FROM QHDOK_ZAK WHERE TYP_DOK='POB'
) AS [zakázka__filter]) AS [t]
        WHERE [id_zakázky] = :zakázka_id AND [stav_zakázky] = 1) AS [t1],
        (SELECT COUNT([id_artiklu]) AS [n_výpadky]
        FROM (SELECT [t5].[id_řádku_zakázky], [t5].[id_zakázky], [t5].[id_artiklu], [t5].[množství], [t7].[kód_skladu], [t7].[disponibilní_pro_ověření], COALESCE([t7].[disponibilní_pro_ověření], 0) AS [disponibilní]
                FROM (SELECT [t2].[id_řádku_zakázky], [t2].[id_zakázky], [t2].[id_artiklu], [t2].[množství]
                        FROM (SELECT [IDSDOK] AS [id_řádku_zakázky], [IDHDOK] AS [id_zakázky], [IDZBOZI] AS [id_artiklu], [MNCELKEMPOCET] AS [množství]
                                FROM [dbo].[QSDOK_ZAK]) AS [t2]
                            INNER JOIN (SELECT *
                                FROM (SELECT [IDHDOK] AS [id_zakázky], [CIS_DOK] AS [číslo_zakázky], [IDSUBJEKT] AS [id_subjektu], [VLSTAVHDOK] AS [stav_zakázky]
                                        FROM (
    SELECT * FROM QHDOK_ZAK WHERE TYP_DOK='POB'
) AS [zakázka__filter]) AS [t3]
                                WHERE [id_zakázky] = :zakázka_id AND [stav_zakázky] = 1) AS [t4] ON [t2].[id_zakázky] = [t4].[id_zakázky]) AS [t5]
                    LEFT JOIN (SELECT *
                        FROM (SELECT [IDZBOZI] AS [id_artiklu], [CIS_SKLAD] AS [kód_skladu], [DISPONIBILNI] AS [disponibilní_pro_ověření]
                                FROM [dbo].[QZBOZSKL]) AS [t6]
                        WHERE [kód_skladu] = :sklad) AS [t7] ON [t5].[id_artiklu] = [t7].[id_artiklu]) AS [t8]
        WHERE [t8].[disponibilní] < [t8].[množství]) AS [t11]
WHERE [t11].[n_výpadky] = 0
