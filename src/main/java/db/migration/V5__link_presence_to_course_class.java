package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;

/** Confere os dados legados antes de qualquer DDL, inclusive no H2. */
public class V5__link_presence_to_course_class extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        requireNoRows(connection, """
                SELECT p.id FROM presence p
                LEFT JOIN course_class c ON c.course_id = p.course_id AND c.class_day = p.date
                GROUP BY p.id HAVING COUNT(c.id) <> 1
                """, "V5: existem presenças sem correspondência única de aula por curso/data. "
                + "Revise aulas ausentes, datas nulas ou aulas ambíguas antes de tentar novamente.");
        requireNoRows(connection, """
                SELECT person_id, course_id, date FROM presence
                GROUP BY person_id, course_id, date HAVING COUNT(*) > 1
                """, "V5: existem presenças duplicadas para a mesma pessoa/aula. "
                + "Revise os registros antes de tentar novamente; nenhum será removido automaticamente.");

        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE presence ADD COLUMN course_class_id BIGINT");
            statement.execute("""
                    UPDATE presence SET course_class_id = (
                        SELECT c.id FROM course_class c
                        WHERE c.course_id = presence.course_id AND c.class_day = presence.date
                    )
                    """);
            statement.execute("ALTER TABLE presence ALTER COLUMN course_class_id SET NOT NULL");
            statement.execute("ALTER TABLE presence ADD CONSTRAINT fk_presence_course_class "
                    + "FOREIGN KEY (course_class_id) REFERENCES course_class(id)");
            statement.execute("ALTER TABLE presence ADD CONSTRAINT uk_presence_person_class "
                    + "UNIQUE (person_id, course_class_id)");
            statement.execute("CREATE INDEX idx_presence_class_course ON presence (course_class_id, course_id)");
            statement.execute("DROP INDEX idx_presence_date_course");
            statement.execute("ALTER TABLE presence DROP COLUMN date");
        }
    }

    private void requireNoRows(Connection connection, String query, String message) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            if (rows.next()) {
                throw new SQLException(message);
            }
        }
    }
}
