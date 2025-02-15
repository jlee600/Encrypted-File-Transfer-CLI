// importing SendSafely API
import com.sendsafely.Package;
import com.sendsafely.Recipient;
import com.sendsafely.SendSafely;
import com.sendsafely.dto.PackageURL;
import com.sendsafely.dto.UserInformation;
import com.sendsafely.ProgressInterface;
import com.sendsafely.file.FileManager;
import com.sendsafely.file.DefaultFileManager;
import com.sendsafely.exceptions.*;

// importing Java util
import java.io.File;
import java.text.MessageFormat;
import java.util.Scanner;
import java.util.Stack;

public class SendSafely_Mock {

    private static SendSafely client;
    private static Package pkgInfo;
    private static Stack<Command> undoStack = new Stack<>();
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("Welcome to the SendSafely");

        try {
            authenticate();
            pkgInfo = client.createPackage();
            System.out.println("Empty package created with ID: " + pkgInfo.getPackageId());
            interactiveLoop();
        } catch (InvalidCredentialsException e) {
            System.out.println("Authentication failed: " + e.getMessage());
        } catch (CreatePackageFailedException e) {
            System.out.println("Package creation failed: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Initialization error: " + e.getMessage());
        }
    }

    // Authenticate user with SendSafely API credentials.
    private static void authenticate() throws Exception {
        System.out.print("Enter your SendSafely API Key: ");
        String apiKey = scanner.nextLine().trim();
        System.out.print("Enter your SendSafely API Secret: ");
        String apiSecret = scanner.nextLine().trim();

        // Initialize SendSafely API Client with the proper URL
        client = new SendSafely("https://app.sendsafely.com", apiKey, apiSecret);

        // Retrieve user information from the API
        UserInformation userInformation = client.getUserInformation();
        System.out.println("\nHello " + userInformation.getFirstName() + "!");
        System.out.println("Successfully connected to SendSafely as " + userInformation.getEmail());
    }

    /**
     * Interactive CLI loop for user commands.
     */
    private static void interactiveLoop() {
        boolean exit = false;
        while (!exit) {
            System.out.println("\nAvailable commands:");
            System.out.println("  upload   - Upload a file");
            System.out.println("  add      - Add a recipient");
            System.out.println("  finalize - Finalize package and get secure link");
            System.out.println("  undo     - Undo last action");
            System.out.println("  exit     - Exit the application");
            System.out.print("Enter command: ");
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
                    System.out.println("Exiting...");
                    break;
                default:
                    System.out.println("Invalid command. Try again.");
                    break;
            }
        }
    }

    // Handles file uploads with progress reporting.
    private static void handleUpload() {
        System.out.print("Enter file path: ");
        String filePath = scanner.nextLine().trim();
        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            System.out.println("Error: File does not exist or is not a valid file.");
            return;
        }

        try {
            System.out.println("Uploading file: " + file.getName());
            Progress progressCallback = new Progress(file.getName());
            FileManager fileManager = new DefaultFileManager(new java.io.File(filePath));
            com.sendsafely.File addedFile = client.encryptAndUploadFile(pkgInfo.getPackageId(), pkgInfo.getKeyCode(), fileManager, progressCallback);
            System.out.println("File uploaded with ID: " + addedFile.getFileId());
            undoStack.push(new UploadFileCommand(pkgInfo.getPackageId(), addedFile.getFileId(), client));
        } catch (UploadFileException e) {
            System.out.println("File upload failed: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Unexpected error during file upload: " + e.getMessage());
        }
    }

    // Handles adding a recipient.
    private static void handleAddRecipient() {
        System.out.print("Enter recipient email: ");
        String email = scanner.nextLine().trim();

        if (email.isEmpty() || !email.contains("@")) {
            System.out.println("Invalid email.");
            return;
        }

        try {
            Recipient newRecipient = client.addRecipient(pkgInfo.getPackageId(), email);
            System.out.println("Recipient added - ID#: " + newRecipient.getRecipientId());
            undoStack.push(new AddRecipientCommand(pkgInfo.getPackageId(), newRecipient.getRecipientId(), client));
        } catch (RecipientFailedException e) {
            System.out.println("Failed to add recipient: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Unexpected error during recipient addition: " + e.getMessage());
        }
    }

    // Finalizes the package and retrieves the secure link.
    private static void handleFinalize() {
        try {
            PackageURL secureLink = client.finalizePackage(pkgInfo.getPackageId(), pkgInfo.getKeyCode());
            System.out.println("Package was finalized. The package can be downloaded from the following URL: " + secureLink.getSecureLink());
        } catch (FinalizePackageFailedException e) {
            System.out.println("Finalization failed: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Unexpected error during finalization: " + e.getMessage());
        }
    }

    // Handles undoing the last action.
    private static void handleUndo() {
        if (undoStack.isEmpty()) {
            System.out.println("No actions to undo.");
            return;
        }
        Command lastAction = undoStack.pop();
        try {
            lastAction.undo();
            System.out.println("Undo successful.");
        } catch (SendFailedException e) {
            System.out.println("Undo failed (SendSafely error): " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Undo failed: " + e.getMessage());
        }
    }
}

// Command interface for undoable actions.
interface Command {
    void undo() throws Exception;
}

// Undo command for file uploads.
class UploadFileCommand implements Command {
    private final String packageId;
    private final String fileId;
    private final SendSafely client;

    public UploadFileCommand(String packageId, String fileId, SendSafely client) {
        this.packageId = packageId;
        this.fileId = fileId;
        this.client = client;
    }

    @Override
    public void undo() throws Exception {
        client.deleteTempPackage(packageId);
        System.out.println("File with ID " + fileId + " removed from package.");
    }
}

// Undo command for adding a recipient.
class AddRecipientCommand implements Command {
    private final String packageId;
    private final String recipientId;
    private final SendSafely client;

    public AddRecipientCommand(String packageId, String recipientId, SendSafely client) {
        this.packageId = packageId;
        this.recipientId = recipientId;
        this.client = client;
    }

    @Override
    public void undo() throws Exception {
        try {
            client.removeRecipient(packageId, recipientId);
            System.out.println("Recipient " + recipientId + " removed from package.");
        } catch (RecipientFailedException e) {
            throw new Exception("Could not undo add recipient: " + e.getMessage());
        }
    }
}

// Callback class that implements the ProgressInterface for showing upload progress.
class Progress implements ProgressInterface {
    private final String fileName;

    public Progress(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void updateProgress(String fileId, double progress) {
        System.out.println(MessageFormat.format("Uploading {0}: {1,number,#.##%}", fileName, progress));
    }

    @Override
    public void gotFileId(String fileId) {
        // dummy function
    }
}
