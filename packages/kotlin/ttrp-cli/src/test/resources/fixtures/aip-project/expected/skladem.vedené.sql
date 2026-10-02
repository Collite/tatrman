SELECT "t2"."id_řádku_zakázky", "t2"."id_zakázky", "t2"."id_artiklu", "t2"."množství"
FROM (SELECT "t"."id_řádku_zakázky", "t"."id_zakázky", "t"."id_artiklu", "t"."množství"
        FROM (SELECT "IDSDOK" AS "id_řádku_zakázky", "IDHDOK" AS "id_zakázky", "IDZBOZI" AS "id_artiklu", "MNCELKEMPOCET" AS "množství"
                FROM "QSDOK_ZAK") AS "t"
            INNER JOIN (SELECT *
                FROM (SELECT "IDHDOK" AS "id_zakázky", "CIS_DOK" AS "číslo_zakázky", "IDSUBJEKT" AS "id_subjektu", "VLSTAVHDOK" AS "stav_zakázky"
                        FROM (
    SELECT * FROM QHDOK_ZAK WHERE TYP_DOK='POB'
) AS "zakázka__filter") AS "t0"
                WHERE "id_zakázky" = :zakázka_id AND "stav_zakázky" = 1) AS "t1" ON "t"."id_zakázky" = "t1"."id_zakázky") AS "t2"
    INNER JOIN (SELECT "IDZBOZI" AS "id_artiklu"
        FROM "QZBOZSKL"
        GROUP BY "IDZBOZI") AS "t4" ON "t2"."id_artiklu" = "t4"."id_artiklu"
