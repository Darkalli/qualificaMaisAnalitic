package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.SQLException;

/** Valida a correspondência antes de alterar o schema, inclusive em bancos sem DDL transacional. */
public class V4__link_register_to_course extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        // Não criar cursos a partir de interesses nem escolher entre nomes duplicados.
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT COUNT(*) FROM (
                         SELECT r.id FROM register r
                         LEFT JOIN course c ON c.name = r.course_of_interest
                         GROUP BY r.id HAVING COUNT(c.id) <> 1
                     ) unresolved
                     """)) {
            rows.next();
            if (rows.getLong(1) > 0) {
                throw new SQLException("V4: existem inscrições sem correspondência única de curso. "
                        + "Cada course_of_interest antigo deve corresponder exatamente ao nome de um único "
                        + "curso cadastrado. Revise o catálogo antes de executar a migração novamente.");
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE register ADD COLUMN course_id BIGINT REFERENCES course(id)");
            statement.execute("UPDATE register SET course_id = "
                    + "(SELECT c.id FROM course c WHERE c.name = register.course_of_interest)");
            statement.execute("ALTER TABLE register ALTER COLUMN course_id SET NOT NULL");
            statement.execute("ALTER TABLE register DROP CONSTRAINT uk_register_person_course");
            statement.execute("ALTER TABLE register ADD CONSTRAINT uk_register_person_course UNIQUE (person_id, course_id)");
            statement.execute("ALTER TABLE register DROP COLUMN course_of_interest");
            statement.execute("CREATE INDEX idx_register_course ON register (course_id)");
        }
    }
}
