package org.group4.quizapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/create-question")
public class QuestionController {

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.username}")
    private String databaseUsername;

    @Value("${spring.datasource.password}")
    private String databasePassword;

    // Method to show the form for creating a new question and display user's existing questions
    @GetMapping
    public String showCreateQuestionForm(Model model, HttpSession session) {
        Long userId = (Long) session.getAttribute("id");
        if (userId == null) {
            return "redirect:/login"; // Redirect to login page if user is not logged in
        }

        model.addAttribute("questionForm", new Question());  // Empty form for new question

        List<Question> questions = new ArrayList<>();

        // Fetch user's questions from the database
        try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword)) {
            String query = "SELECT id, question_text, answer, description, tags, type, options FROM questions WHERE user_id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setLong(1, userId);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        Question question = new Question();
                        question.setId(resultSet.getLong("id"));
                        question.setQuestionText(resultSet.getString("question_text"));
                        question.setAnswer(resultSet.getString("answer"));
                        question.setDescription(resultSet.getString("description"));
                        String tagsString = resultSet.getString("tags");
                        if (tagsString != null && !tagsString.isEmpty()) {
                            question.setTags(new ArrayList<>(Arrays.asList(tagsString.split(","))));
                        }
                        question.setType(resultSet.getString("type")); // Set the type
                        String optionsString = resultSet.getString("options");
                        if (optionsString != null && !optionsString.isEmpty()) {
                            question.setOptions(new ArrayList<>(Arrays.asList(optionsString.split(","))));
                        }
                        questions.add(question);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "error-page";
        }

        model.addAttribute("questions", questions);
        return "create-question";
    }

    @PostMapping
    public String createQuestion(
            @ModelAttribute("questionForm") Question questionForm,
            @RequestParam(value = "options", required = false) List<String> options,
            HttpSession session) {

        Long userId = (Long) session.getAttribute("id");
        if (userId == null) {
            return "redirect:/login"; // Redirect to login page if user is not logged in
        }

        questionForm.setUserId(userId);

        // Handle options for multiple choice types
        if (options == null || options.isEmpty()) {
            questionForm.setOptions(new ArrayList<>()); // Empty list
        } else {
            questionForm.setOptions(options); // Set provided list
        }

        //Comma separated options for database storage
        String serializedOptions = String.join(",", questionForm.getOptions());

        // Adjust answer for True/False question type
        if ("TrueFalse".equalsIgnoreCase(questionForm.getType())) {
            if ((questionForm.getAnswer()).contains("True")) {
                questionForm.setAnswer("True"); // Set to "True" if the answer contains True
            } else if ((questionForm.getAnswer()).contains("False")) {
                questionForm.setAnswer("False"); // Set to "False" if the answer contains False
            } else {
                questionForm.setAnswer("");
            }
        }

        // Save to database
        try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword)) {
            String query = "INSERT INTO questions (user_id, question_text, answer, description, tags, type, options) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setLong(1, questionForm.getUserId());
                preparedStatement.setString(2, questionForm.getQuestionText());
                preparedStatement.setString(3, questionForm.getAnswer());
                preparedStatement.setString(4, questionForm.getDescription());
                preparedStatement.setString(5, String.join(",", questionForm.getTags()));
                preparedStatement.setString(6, questionForm.getType());
                preparedStatement.setString(7, serializedOptions);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "error-page";
        }

        return "redirect:/create-question";
    }

    // Method to delete a question
    @PostMapping("/delete")
    public String deleteQuestion(@RequestParam("id") Long questionId, HttpSession session) {
        Long userId = (Long) session.getAttribute("id");
        if (userId == null) {
            return "redirect:/login"; // Redirect to login page if user is not logged in
        }

        // Delete the question from the database
        try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword)) {
            String query = "DELETE FROM questions WHERE id = ? AND user_id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setLong(1, questionId);
                preparedStatement.setLong(2, userId);
                int rowsAffected = preparedStatement.executeUpdate();
                if (rowsAffected == 0) {
                    return "error-page"; // Show an error page if no rows were affected
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "error-page";
        }

        return "redirect:/create-question";
    }

    @PostMapping("/filter-search")
    public String filterAndSearchQuestions(
            @RequestParam(value = "qText", required = false) String qText,
            @RequestParam(value = "tagFilter", required = false) String tagFilter,
            @RequestParam(value = "typeFilter", required = false) String typeFilter,
            Model model, HttpSession session) {

        Long userId = (Long) session.getAttribute("id");
        if (userId == null) {
            return "redirect:/login"; // Redirect to login page if user is not logged in
        }

        List<Question> filteredQuestions = new ArrayList<>();

        // Construct the query dynamically based on provided filters
        StringBuilder queryBuilder = new StringBuilder("SELECT id, question_text, answer, description, tags, type FROM questions WHERE user_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);

        if (qText != null && !qText.trim().isEmpty()) {
            queryBuilder.append(" AND question_text LIKE ?");
            parameters.add("%" + qText.trim() + "%");
        }

        if (tagFilter != null && !tagFilter.trim().isEmpty()) {
            queryBuilder.append(" AND tags LIKE ?");
            parameters.add("%" + tagFilter.trim() + "%");
        }

        if (typeFilter != null && !typeFilter.trim().isEmpty()) {
            queryBuilder.append(" AND type = ?");
            parameters.add(typeFilter.trim());
        }

        try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword);
             PreparedStatement preparedStatement = connection.prepareStatement(queryBuilder.toString())) {

            // Set query parameters dynamically
            for (int i = 0; i < parameters.size(); i++) {
                preparedStatement.setObject(i + 1, parameters.get(i));
            }

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    Question question = new Question();
                    question.setId(resultSet.getLong("id"));
                    question.setQuestionText(resultSet.getString("question_text"));
                    question.setAnswer(resultSet.getString("answer"));
                    question.setDescription(resultSet.getString("description"));
                    question.setType(resultSet.getString("type"));
                    String tagsString = resultSet.getString("tags");
                    if (tagsString != null && !tagsString.isEmpty()) {
                        question.setTags(new ArrayList<>(Arrays.asList(tagsString.split(","))));
                    }
                    filteredQuestions.add(question);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "error-page";
        }

        // Pass the filtered results to the view
        model.addAttribute("questions", filteredQuestions);
        return "create-question"; // Render the same page with filtered results
    }
}
