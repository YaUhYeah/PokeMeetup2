import os
import javalang
from collections import defaultdict


def extract_dependencies(java_file_path):
    """
    Parse a Java file and extract dependencies from its imports and usage.
    """
    with open(java_file_path, 'r') as file:
        java_code = file.read()

    tree = javalang.parse.parse(java_code)
    imports = [imp.path for imp in tree.imports]
    class_name = tree.types[0].name  # Assumes one public class per file
    dependencies = set()

    def visit_node(node):
        """
        Recursively visit all nodes in the AST to collect types.
        """
        if isinstance(node, javalang.tree.MethodInvocation):
            if node.qualifier:
                dependencies.add(node.qualifier)
        for child in node.children:
            if isinstance(child, (list, tuple)):
                for item in child:
                    if isinstance(item, javalang.ast.Node):
                        visit_node(item)
            elif isinstance(child, javalang.ast.Node):
                visit_node(child)

    for path, node in tree:
        visit_node(node)

    return class_name, imports, dependencies


def find_required_classes(main_class_path, project_root):
    """
    Find all required classes for a Java server project based on the main class.
    """
    main_class, imports, initial_dependencies = extract_dependencies(main_class_path)
    required_classes = set(imports).union(initial_dependencies)

    # Map imports to file paths
    import_to_path = {}
    for root, _, files in os.walk(project_root):
        for file in files:
            if file.endswith(".java"):
                relative_path = os.path.relpath(os.path.join(root, file), project_root)
                package_path = relative_path.replace(os.sep, '.')[:-5]  # Remove '.java'
                import_to_path[package_path] = os.path.join(root, file)

    # Resolve class paths
    resolved_classes = set()
    unresolved_classes = list(required_classes)

    while unresolved_classes:
        current_class = unresolved_classes.pop()
        if current_class in resolved_classes:
            continue
        resolved_classes.add(current_class)

        if current_class in import_to_path:
            _, imports, dependencies = extract_dependencies(import_to_path[current_class])
            for dep in imports.union(dependencies):
                if dep not in resolved_classes:
                    unresolved_classes.append(dep)

    return resolved_classes


if __name__ == "__main__":
    # Path to the main server class
    main_class_file = "core/src/main/java/io/github/pokemeetup/server/deployment/ServerLauncher.java    "
    project_directory = "/"

    required_classes = find_required_classes(main_class_file, project_directory)

    print("Classes required for the server module:")
    for cls in sorted(required_classes):
        print(cls)
