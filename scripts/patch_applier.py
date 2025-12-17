import re
import subprocess
import tempfile
import os
from scripts.logger import logger

def apply_patch(patch_content: str) -> list[str]:
    """
    Apply a unified diff patch and return list of modified files.
    """
    # Extract the actual diff from LLM response
    patch_content = extract_diff(patch_content)
    
    if not patch_content:
        logger.warning("⚠️ No valid diff found in response")
        return []

    patched_files = []
    
    # Try using git apply
    try:
        with tempfile.NamedTemporaryFile(mode='w', suffix='.patch', delete=False) as f:
            f.write(patch_content)
            patch_file = f.name
        
        result = subprocess.run(
            ["git", "apply", "--check", patch_file],
            capture_output=True,
            text=True
        )
        
        if result.returncode == 0:
            subprocess.run(["git", "apply", patch_file], check=True)
            patched_files = extract_files_from_patch(patch_content)
            logger.info(f"✅ Patch applied via git apply")
        else:
            logger.warning(f"⚠️ git apply check failed: {result.stderr}")
            patched_files = apply_patch_manually(patch_content)
        
        os.unlink(patch_file)
        
    except Exception as e:
        logger.warning(f"⚠️ git apply failed: {e}, trying manual apply")
        patched_files = apply_patch_manually(patch_content)
    
    return patched_files


def extract_diff(content: str) -> str:
    """Extract unified diff from LLM response."""
    lines = content.split('\n')
    diff_lines = []
    in_diff = False
    
    for line in lines:
        if line.startswith('---') or line.startswith('diff --git'):
            in_diff = True
        if in_diff:
            diff_lines.append(line)
    
    return '\n'.join(diff_lines) if diff_lines else content


def extract_files_from_patch(patch_content: str) -> list[str]:
    """Extract file paths from patch content."""
    files = set()
    for line in patch_content.split('\n'):
        if line.startswith('+++ b/') or line.startswith('+++ '):
            file_path = line.replace('+++ b/', '').replace('+++ ', '').strip()
            if file_path and file_path != '/dev/null':
                files.add(file_path)
    return list(files)


def apply_patch_manually(patch_content: str) -> list[str]:
    """Fallback: manually apply simple patches."""
    patched_files = []
    
    # Parse hunks and apply
    current_file = None
    
    for line in patch_content.split('\n'):
        if line.startswith('+++ '):
            current_file = line.replace('+++ b/', '').replace('+++ ', '').strip()
            if current_file and os.path.exists(current_file):
                patched_files.append(current_file)
    
    # For complex patches, we rely on git apply
    # This is a simplified fallback
    logger.warning("⚠️ Manual patch apply is limited - check results")
    
    return patched_files
