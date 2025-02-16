// importing SendSafely API
import com.sendsafely.Package;
import com.sendsafely.Recipient;
import com.sendsafely.SendSafely;
import com.sendsafely.dto.PackageURL;
import com.sendsafely.ProgressInterface;
import com.sendsafely.File;
import com.sendsafely.file.FileManager;
import com.sendsafely.file.DefaultFileManager;
import com.sendsafely.exceptions.*;

// importing Java util
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Scanner;
import java.util.Stack;

public class SendSafely_Mock {

    private static SendSafely client;
    private static Package pkgInfo;
    private static Stack<Command> undoStack = new Stack<>();
    private static Scanner scanner = new Scanner(System.in);

    public static final String red = "\u001B[31m";
    public static final String reset = "\u001B[0m";
    public static final String green = "\u001B[32m";

    public static void main(String[] args) {
        System.out.println("*** Welcome to the SendSafely ***");
        try {
            // authentication & create empty package
            authenticate();
            pkgInfo = client.createPackage();
            System.out.println("Empty package created with ID: " + pkgInfo.getPackageId());
            // if authorized, prompt user command
            interactiveLoop();
        } catch (InvalidCredentialsException e) {
            System.out.println(red + "Authentication failed: " + e.getMessage() + reset);
        } catch (CreatePackageFailedException e) {
            System.out.println(red +"Package creation failed: " + e.getMessage() + reset);
        } catch (LimitExceededException e) {
            System.out.println(red +"Package Limit Exceeded: " + e.getMessage() + reset);
        } catch (UserInformationFailedException e) {
            System.out.println(red +"User information retrieval failed: " + e.getMessage() + reset);
        } catch (Exception e) {
            System.out.println(red +"Unexpected error during initiation: " + e.getMessage() + reset);
        }
    }

    // Authenticate user with SendSafely API credentials.
    private static void authenticate() throws InvalidCredentialsException, UserInformationFailedException {
        System.out.print("Enter your SendSafely API Key: ");
        String apiKey = scanner.nextLine().trim();
        System.out.print("Enter your SendSafely API Secret: ");
        String apiSecret = scanner.nextLine().trim();

        // Initialize SendSafely API Client with the proper URL
        client = new SendSafely("https://app.sendsafely.com", apiKey, apiSecret);

        // Retrieve user email from the API
        String userEmail = client.verifyCredentials();
        String fname = client.getUserInformation().getFirstName();
        System.out.println("\nHello " + fname);
        System.out.println("Successfully connected to SendSafely as " + userEmail);
    }

    // Interactive loop for user commands.
    private static void interactiveLoop() {
        boolean exit = false;
        while (!exit) {
            System.out.println("\nAvailable commands:");
            System.out.println("  upload   - Upload a file");
            System.out.println("  add      - Add a recipient");
            System.out.println("  finalize - Finalize package and get secure link");
            System.out.println("  undo     - Undo last action");
            System.out.println("  exit     - Exit the application");
            System.out.print(green + "Enter command: " + reset);
            String command = scanner.nextLine().trim().toLowerCase();

            switch (command) {
                case "upload":
                    handleUpload();
                    break;
                case "add":
                    handleAddRecipient();
                    break;
                case "finalize":
                    handleFinalize();
                    break;
                case "undo":
                    handleUndo();
                    break;
                case "exit":
                    exit = true;
                    System.out.println(red + "Good Bye" + reset);
                    break;
                default:
                    System.out.println(red + "Invalid command. Try again." + reset);
                    break;
            }
        }
    }

    // Handles file uploads
    private static void handleUpload() {
        System.out.print("Enter file path in absolute path format: ");
        String filePath = scanner.nextLine().trim();

        try {
            System.out.println("Uploading file");
            FileManager fileManager = new DefaultFileManager(new java.io.File(filePath));
            // encrypt and upload file + progress
            File addedFile = client.encryptAndUploadFile(pkgInfo.getPackageId(), pkgInfo.getKeyCode(), fileManager, new Progress());
            System.out.println("File uploaded with ID: " + addedFile.getFileId());
            undoStack.push(new UploadFileCommand(pkgInfo.getPackageId(), pkgInfo.getRootDirectoryId(), addedFile.getFileId(), client));
        } catch (IOException e) {
            System.out.println(red + "IO Exception: " + e.getMessage() + reset);
        } catch (UploadFileException e) {
            System.out.println(red + "File upload failed: " + e.getMessage() + reset);
        } catch (LimitExceededException e) {
            System.out.println(red + "File Limit Exceeded: " + e.getMessage() + reset);
        } catch (Exception e) {
            System.out.println(red + "Unexpected error during file upload: " + e.getMessage() + reset);
        }
    }

    // Handles adding a recipient.
    private static void handleAddRecipient() {
        System.out.print("Enter recipient email in format <user@example.com>: ");
        String email = scanner.nextLine().trim();

        // edge cases
        if (email.isEmpty() || !email.contains("@")) {
            System.out.println(red + "Invalid email." + reset);
            return;
        }

        try {
            Recipient newRecipient = client.addRecipient(pkgInfo.getPackageId(), email);
            System.out.println("Recipient added - ID#: " + newRecipient.getRecipientId());
            undoStack.push(new AddRecipientCommand(pkgInfo.getPackageId(), newRecipient.getRecipientId(), newRecipient.getEmail(), client));
        } catch (LimitExceededException e) {
            System.out.println(red + "Recipient Limit Exceeded: " + e.getMessage() + reset);
        } catch (RecipientFailedException e) {
            System.out.println(red + "Failed to add recipient: " + e.getMessage() + reset);
        } catch (Exception e) {
            System.out.println(red + "Unexpected error during recipient addition: " + e.getMessage() + reset);
        }
    }

    // Finalizes the package and retrieves the secure link.
    private static void handleFinalize() {
        try {
            PackageURL secureLink = client.finalizePackage(pkgInfo.getPackageId(), pkgInfo.getKeyCode(), true);
            System.out.println("Package was finalized. The package can be downloaded from the following URL: " + secureLink.getSecureLink());
            System.out.println("Notify Package recipients status: " + secureLink.getNotificationStatus());
        } catch (LimitExceededException e) {
            System.out.println(red + "Package Limit Exceeded: " + e.getMessage() + reset);
        } catch (FinalizePackageFailedException e) {
            System.out.println(red + "Finalization failed: " + e.getMessage() + reset);
        } catch (ApproverRequiredException e) {
            System.out.println(red + "Approve Required: " + e.getMessage() + reset);
        } catch (Exception e) {
            System.out.println(red + "Unexpected error during finalization: " + e.getMessage() + reset);
        }
    }

    // Handles undoing the last action.
    private static void handleUndo() {
        // edge case
        if (undoStack.isEmpty()) {
            System.out.println(red + "No actions to undo." + reset);
            return;
        }
        Command lastAction = undoStack.pop();
        try {
            lastAction.undo();
            System.out.println("Undo successful.");
        } catch (Exception e) {
            System.out.println(red + "Undo failed: " + e.getMessage() + reset);
        }
    }
}

// Command interface for undoable actions.
interface Command {
    void undo();
}

// Undo command for file uploads.
class UploadFileCommand implements Command {

    private final String packageId;
    private final String directoryId;
    private final String fileId;
    private final SendSafely client;

    public static final String red = "\u001B[31m";
    public static final String reset = "\u001B[0m";

    public UploadFileCommand(String packageId, String directoryId, String fileId, SendSafely client) {
        this.packageId = packageId;
        this.directoryId = directoryId;
        this.fileId = fileId;
        this.client = client;
    }

    @Override
    public void undo() {
        try {
            // delete only the file but not the package
            client.deleteFile(packageId, directoryId, fileId);
            System.out.println("File with ID " + fileId + " removed from package.");
        } catch (FileOperationFailedException e) {
            System.out.println(red + "Cannot Undo upload: " + e.getMessage() + reset);
        } catch (Exception e) {
            System.out.println(red + "Unexpected error during upload undo: " + e.getMessage() + reset);
        }
    }
}

// Undo command for adding a recipient.
class AddRecipientCommand implements Command {

    private final String packageId;
    private final String recipientId;
    private final String email;
    private final SendSafely client;

    public static final String red = "\u001B[31m";
    public static final String reset = "\u001B[0m";

    public AddRecipientCommand(String packageId, String recipientId, String email, SendSafely client) {
        this.packageId = packageId;
        this.recipientId = recipientId;
        this.email = email;
        this.client = client;
    }

    @Override
    public void undo() {
        try {
            client.removeRecipient(packageId, recipientId);
            System.out.println("Recipient " + email + " removed from package.");
        } catch (RecipientFailedException e) {
            System.out.println(red + "Could not undo add recipient: " + e.getMessage() + reset);
        } catch (Exception e) {
            System.out.println(red + "Unexpected error during add recipient undo: " + e.getMessage() + reset);
        }
    }
}

// Callback class that implements the ProgressInterface for showing upload progress.
class Progress implements ProgressInterface {
    @Override
    public void updateProgress(String fileId, double progress) {
        System.out.println(MessageFormat.format("Uploading: {0,number,#.##%}", progress));
    }
    @Override
    public void gotFileId(String fileId) {
        // dummy function
    }
}
