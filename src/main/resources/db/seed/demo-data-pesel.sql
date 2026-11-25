-- A PESEL in a customer's comment on RET-1045, for the chat's personal-data masking: the model reads a placeholder,
-- support staff read the number. 85071801246 is fictional (born 1985-07-18, female, valid checksum). The comment has
-- a fixed id so the changeset's rollback finds it. Avoid semicolons inside string literals: Liquibase splits this file
-- on them.

INSERT INTO ticket_comment (id, ticket_id, author_id, internal, body, created_at)
SELECT '00000000-0000-0000-0000-0000000e0001', t.id, u.id, false,
       'For the direct debit mandate the bank needs the signatory: Irene Walsh, PESEL 85071801246.',
       t.created_at + interval '3 hours'
FROM ticket t
JOIN app_user u ON u.email = 'irene.walsh@initech.example'
WHERE t.id = '00000000-0000-0000-0000-0000000d0042';

UPDATE ticket t
SET updated_at = greatest(t.updated_at, c.created_at)
FROM ticket_comment c
WHERE c.id = '00000000-0000-0000-0000-0000000e0001'
  AND t.id = c.ticket_id;
